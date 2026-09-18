package id.umarflab.murotalaudioeditor

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Comparison only, not a device-dependent pass/fail speed threshold. */
class ExportBenchmarkTest {
    @Test fun repeatedClipsExportTiming() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File(context.cacheDir,"benchmark.wav")
        val second=ByteBuffer.allocate(44100*4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(44100) { i -> val value=(sin(2*PI*440*i/44100)*4000).toInt().toShort(); second.putShort(value); second.putShort(value) }
        source.outputStream().buffered().use { output -> output.write(wavHeader(44100L*4*10)); repeat(10) { output.write(second.array()) } }
        val clip=AudioClip(uri=Uri.fromFile(source).toString(),name="Benchmark",sourceDurationMs=10000)
        val project=EditorProject(layers=(0..1).map { track -> AudioLayer(name="Track ${track+1}",clips=(0..2).map { index -> clip.copy(id="$track-$index",timelineStartMs=index*10000L,volume=.5f) }) })
        val output=File(context.cacheDir,"benchmark.m4a")
        val startOld=SystemClock.elapsedRealtime()
        LegacyAudioExport(context).render(project,Uri.fromFile(output),"M4A",192000) {}
        val oldMs=SystemClock.elapsedRealtime()-startOld
        val startNew=SystemClock.elapsedRealtime()
        AudioExport(context).render(project,Uri.fromFile(output),"M4A",192000) {}
        val newMs=SystemClock.elapsedRealtime()-startNew
        val directory=File(context.getExternalFilesDir(null),"ui-checks").apply { mkdirs() }
        File(directory,"export-benchmark.txt").writeText("Android emulator API 29 x86_64. 30-second mix; 2 tracks, 6 identical 10-second WAV clips; M4A 192 kbps. Single run per implementation, legacy first. Warm filesystem/cache may favor second run.\nLegacy: $oldMs ms\nUpdated: $newMs ms\nNot a guarantee of phone performance.\n")
        preserveTestArtifacts(directory)
        source.delete();output.delete()
        Unit
    }
}
