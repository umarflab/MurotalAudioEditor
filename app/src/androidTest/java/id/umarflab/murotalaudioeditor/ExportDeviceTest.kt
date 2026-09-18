package id.umarflab.murotalaudioeditor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import android.net.Uri
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class ExportDeviceTest {
    @Test fun testMixSaveOpenAndAac() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File(context.cacheDir,"test-source.wav")
        val samples=ByteBuffer.allocate(44100*4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(44100) { i -> val s=(sin(2*PI*440*i/44100)*4000).toInt().toShort(); samples.putShort(s); samples.putShort(s) }
        source.writeBytes(wavHeader(samples.capacity().toLong())+samples.array())
        val c=AudioClip(uri=Uri.fromFile(source).toString(),name="tone",sourceDurationMs=1000)
        val p=EditorProject(layers=listOf(AudioLayer(name="Track 1",clips=listOf(c)),AudioLayer(name="Track 2",clips=listOf(c.copy(id="second",timelineStartMs=500)))))
        val projectFile=File(context.cacheDir,"test.mae")
        ProjectStore(context).export(p,Uri.fromFile(projectFile))
        val restored=ProjectStore(context).open(Uri.fromFile(projectFile))
        assertEquals(p,restored)
        val wav=File(context.cacheDir,"test-out.wav")
        AudioExport(context).render(restored,Uri.fromFile(wav),"WAV",192000) {}
        assertEquals(44L+66150*4,wav.length())
        val bytes=wav.readBytes()
        fun rms(from: Int,to: Int): Double {
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var sum=0.0
            for(i in from until to) { val s=buffer.getShort(44+i*4).toDouble(); sum+=s*s }
            return sqrt(sum/(to-from))
        }
        val ratio=rms(30000,40000)/rms(5000,15000)
        assertTrue("Mixed amplitude should double: $ratio",ratio in 1.8..2.2)
        val aac=File(context.cacheDir,"test-out.m4a")
        AudioExport(context).render(p,Uri.fromFile(aac),"M4A",192000) {}
        val metadata=MediaMetadataRetriever()
        try {
            metadata.setDataSource(aac.path)
            val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("AAC duration $duration",duration in 1400..1700)
        } finally { metadata.release() }
        val mp3Sizes=mutableListOf<Long>()
        for(rate in listOf(128000,192000,320000)) {
            val mp3=File(context.cacheDir,"test-$rate.mp3")
            AudioExport(context).render(p,Uri.fromFile(mp3),"MP3",rate) {}
            mp3Sizes.add(mp3.length())
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(mp3.path)
                val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("MP3 $rate duration $duration",duration in 1400..1800)
            } finally { reader.release() }
            // Decode encoded MP3 back to PCM, checking it contains audible samples.
            val decoded=File(context.cacheDir,"mp3-decoded.wav")
            val encodedClip=c.copy(uri=Uri.fromFile(mp3).toString(),sourceDurationMs=1500,trimEndMs=1500)
            AudioExport(context).render(p.copy(layers=listOf(AudioLayer(name="Track 1",clips=listOf(encodedClip)))),Uri.fromFile(decoded),"WAV",192000) {}
            val pcm=ByteBuffer.wrap(decoded.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            var peak=0
            for(i in 44 until pcm.capacity()-1 step 2) peak=max(peak,abs(pcm.getShort(i).toInt()))
            assertTrue("Decoded MP3 is audible",peak>1000)
            decoded.delete();mp3.delete()
        }
        assertTrue(mp3Sizes[0]<mp3Sizes[1] && mp3Sizes[1]<mp3Sizes[2])
        // Exercise speed, pitch, and trim through Sonic, not just model arithmetic.
        val sped=p.copy(layers=listOf(AudioLayer(name="Track 1",clips=listOf(c.copy(trimStartMs=200,trimEndMs=800,speed=2f,pitchSemitones=3f)))))
        AudioExport(context).render(sped,Uri.fromFile(wav),"WAV",192000) {}
        assertEquals(44L+13230*4,wav.length())
        withContext(Dispatchers.Main) {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
            var ended=false
            var lastPosition=0L
            var error:String?=null
            val player=TimelinePlayer(context,scope,{lastPosition=it},{ended=!it},{error=it})
            try {
                player.play(p.layers.flatMap { it.clips },0,1500)
                withTimeout(15000) { while(!ended) delay(50) }
                assertNull(error)
                assertEquals(1500L,lastPosition)
                // Repeated mode switching must not release players of the next session.
                player.play(listOf(c),0,1000)
                delay(50)
                player.play(p.layers.flatMap { it.clips },500,1500)
                withTimeout(15000) { while(!ended) delay(50) }
                assertNull(error)
                assertEquals(1500L,lastPosition)
            } finally { player.stop(); scope.cancel() }
        }
        listOf(source,projectFile,wav,aac).forEach { it.delete() }
    }
}
