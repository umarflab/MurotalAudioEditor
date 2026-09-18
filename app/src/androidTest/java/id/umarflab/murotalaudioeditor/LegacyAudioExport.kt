package id.umarflab.murotalaudioeditor

import android.content.Context
import android.media.*
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Decode and process in bounded buffers, spool to disk, then mix in 4096-frame blocks. */
class LegacyAudioExport(private val context: Context) {
    companion object { const val RATE = 44100 }
    suspend fun render(project: EditorProject, destination: Uri, format: String, bitrate: Int, progress: (String) -> Unit) {
        val all = project.layers.flatMap { it.clips }
        require(all.isNotEmpty()) { "Proyek kosong" }
        val duration = all.maxOf { it.timelineStartMs + it.editedDurationMs }
        val frames = duration * RATE / 1000
        require(frames * 4 < 0xffffffffL - 44) { "Durasi melampaui batas WAV 4 GB" }
        val work=File(context.cacheDir,"render-"+java.util.UUID.randomUUID()).apply { mkdirs() }
        try {
            val clips=all.filterNot { it.muted }
            val needed=clips.sumOf { it.editedDurationMs * RATE / 1000 * 4 } + frames*8 + 32*1024*1024
            require(work.usableSpace > needed) { "Penyimpanan kosong tidak cukup untuk ekspor" }
            val files=mutableListOf<Pair<AudioClip,File>>()
            clips.forEachIndexed { i,c ->
                currentCoroutineContext().ensureActive()
                progress("Memproses audio " + (i+1) + "/" + clips.size)
                val f=File(work,"clip-$i.pcm"); decode(c,f); files.add(c to f)
            }
            val wav=File(work,"mix.wav")
            mix(files,frames,wav,progress)
            val result=if(format=="M4A") File(work,"mix.m4a").also { encodeAac(wav,it,bitrate,progress) } else wav
            currentCoroutineContext().ensureActive()
            progress("Menyimpan hasil")
            requireNotNull(context.contentResolver.openOutputStream(destination,"wt")).use { output ->
                result.inputStream().use { input ->
                    val block=ByteArray(65536)
                    while(true) { currentCoroutineContext().ensureActive(); val n=input.read(block); if(n<0) break; output.write(block,0,n) }
                }
            }
        } finally { work.deleteRecursively() }
    }

    private suspend fun decode(clip: AudioClip, file: File) {
        val extractor=MediaExtractor()
        var codec: MediaCodec?=null
        val sonic=SonicAudioProcessor()
        try {
            extractor.setDataSource(context,Uri.parse(clip.uri),null)
            val track=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
                ?: error("Tidak ada audio: " + clip.name)
            val fmt=extractor.getTrackFormat(track); extractor.selectTrack(track)
            extractor.seekTo(clip.trimStartMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder=MediaCodec.createDecoderByType(requireNotNull(fmt.getString(MediaFormat.KEY_MIME))); codec=decoder
            decoder.configure(fmt,null,null,0); decoder.start()
            var inputEnded=false; var outputEnded=false; var channels=0; var rate=0; var encoding=C.ENCODING_PCM_16BIT
            var configured=false; var idle=0
            val info=MediaCodec.BufferInfo()
            file.outputStream().buffered().use { out ->
                fun drain() {
                    val data=sonic.output
                    val bytes=ByteArray(data.remaining()); data.get(bytes)
                    // Sonic preserves source channel count. Convert mono to stereo.
                    if(channels==1) { var i=0; while(i+1<bytes.size) { out.write(bytes,i,2); out.write(bytes,i,2); i+=2 } }
                    else out.write(bytes)
                }
                while(!outputEnded) {
                    currentCoroutineContext().ensureActive()
                    if(!inputEnded) {
                        val index=decoder.dequeueInputBuffer(10000)
                        if(index>=0) {
                            val input=requireNotNull(decoder.getInputBuffer(index)); input.clear()
                            val size=extractor.readSampleData(input,0)
                            val time=extractor.sampleTime
                            if(size<0 || time>=clip.trimEndMs*1000) {
                                decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded=true
                            } else { decoder.queueInputBuffer(index,0,size,time,0); extractor.advance() }
                        }
                    }
                    val index=decoder.dequeueOutputBuffer(info,10000)
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val of=decoder.outputFormat
                        channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT); rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        require(channels in 1..2) { "Ekspor mendukung sumber mono/stereo" }
                        encoding=if(of.containsKey(MediaFormat.KEY_PCM_ENCODING)) of.getInteger(MediaFormat.KEY_PCM_ENCODING) else C.ENCODING_PCM_16BIT
                        require(encoding==C.ENCODING_PCM_16BIT || encoding==C.ENCODING_PCM_FLOAT) { "Format PCM tidak didukung" }
                        sonic.setSpeed(clip.speed); sonic.setPitch(2.0.pow(clip.pitchSemitones/12.0).toFloat()); sonic.setOutputSampleRateHz(RATE)
                        sonic.configure(AudioProcessor.AudioFormat(rate,channels,C.ENCODING_PCM_16BIT)); sonic.flush(); configured=true
                    } else if(index>=0) {
                        idle=0
                        if(info.size>0) {
                            check(configured)
                            val data=requireNotNull(decoder.getOutputBuffer(index)).duplicate().order(ByteOrder.nativeOrder())
                            val bytesPerSample=if(encoding==C.ENCODING_PCM_FLOAT) 4 else 2
                            val count=info.size/(channels*bytesPerSample)
                            val first=max(0L,ceil((clip.trimStartMs*1000-info.presentationTimeUs)*rate/1000000.0).toLong()).coerceAtMost(count.toLong()).toInt()
                            val last=ceil((clip.trimEndMs*1000-info.presentationTimeUs)*rate/1000000.0).toLong().coerceIn(first.toLong(),count.toLong()).toInt()
                            val pcm=ByteBuffer.allocateDirect((last-first)*channels*2).order(ByteOrder.nativeOrder())
                            data.position(info.offset+first*channels*bytesPerSample)
                            repeat((last-first)*channels) {
                                val value=if(encoding==C.ENCODING_PCM_FLOAT) (data.float.coerceIn(-1f,1f)*32767).toInt().toShort() else data.short
                                pcm.putShort(value)
                            }
                            pcm.flip()
                            if(sonic.isActive) { sonic.queueInput(pcm); drain() }
                            else {
                                val bytes=ByteArray(pcm.remaining()); pcm.get(bytes)
                                if(channels==1) { var i=0; while(i<bytes.size) { out.write(bytes,i,2); out.write(bytes,i,2); i+=2 } } else out.write(bytes)
                            }
                        }
                        outputEnded=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(index,false)
                    } else { idle++; check(idle<3000) { "Decoder tidak merespons: " + clip.name } }
                }
                if(configured && sonic.isActive) { sonic.queueEndOfStream(); drain() }
            }
        } finally { runCatching { codec?.stop() }; codec?.release(); extractor.release(); sonic.reset() }
    }

    private suspend fun mix(files: List<Pair<AudioClip,File>>, total: Long, wav: File, progress: (String)->Unit) {
        val handles=files.map { RandomAccessFile(it.second,"r") }
        try {
            wav.outputStream().buffered().use { out ->
                out.write(wavHeader(total*4))
                val sum=FloatArray(4096*2); val bytes=ByteArray(4096*4)
                var cursor=0L; var lastPercent=-1
                while(cursor<total) {
                    currentCoroutineContext().ensureActive()
                    val count=min(4096L,total-cursor).toInt(); sum.fill(0f)
                    files.forEachIndexed { i,(clip,_) ->
                        val start=clip.timelineStartMs*RATE/1000
                        val end=start+min(handles[i].length()/4,clip.editedDurationMs*RATE/1000)
                        val from=max(cursor,start); val to=min(cursor+count,end)
                        if(to>from) {
                            val length=((to-from)*4).toInt(); handles[i].seek((from-start)*4); handles[i].readFully(bytes,0,length)
                            val pcm=ByteBuffer.wrap(bytes,0,length).order(ByteOrder.LITTLE_ENDIAN)
                            val offset=((from-cursor)*2).toInt()
                            repeat(length/2) { s -> sum[offset+s]+=pcm.short*clip.volume }
                        }
                    }
                    val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    repeat(count*2) { i -> buffer.putShort(sum[i].roundToInt().coerceIn(-32768,32767).toShort()) }
                    out.write(bytes,0,count*4); cursor+=count
                    val percent=(cursor*100/total).toInt()
                    if(percent!=lastPercent) { progress("Mencampur audio $percent%"); lastPercent=percent }
                }
            }
        } finally { handles.forEach { it.close() } }
    }

    private suspend fun encodeAac(wav: File, result: File, bitrate: Int, progress: (String)->Unit) {
        val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer?=null; var started=false
        try {
            val fmt=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,RATE,2)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE,bitrate); fmt.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            val mux=MediaMuxer(result.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); muxer=mux
            var track=-1; var frames=0L; var inputEnded=false; var ended=false; var idle=0
            val info=MediaCodec.BufferInfo()
            RandomAccessFile(wav,"r").use { input ->
                input.seek(44)
                while(!ended) {
                    currentCoroutineContext().ensureActive()
                    if(!inputEnded) {
                        val idx=codec.dequeueInputBuffer(10000)
                        if(idx>=0) {
                            val b=requireNotNull(codec.getInputBuffer(idx)); b.clear()
                            val bytes=ByteArray(min(16384,b.remaining()/4*4)); val n=input.read(bytes)
                            val timestamp=frames*1000000/RATE
                            if(n<0) { codec.queueInputBuffer(idx,0,0,timestamp,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded=true }
                            else { b.put(bytes,0,n); codec.queueInputBuffer(idx,0,n,timestamp,0); frames+=n/4 }
                        }
                    }
                    val idx=codec.dequeueOutputBuffer(info,10000)
                    if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track=mux.addTrack(codec.outputFormat); mux.start(); started=true }
                    else if(idx>=0) {
                        idle=0
                        if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                            val data=requireNotNull(codec.getOutputBuffer(idx)); data.position(info.offset); data.limit(info.offset+info.size)
                            mux.writeSampleData(track,data,info)
                        }
                        ended=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0; codec.releaseOutputBuffer(idx,false)
                    } else { idle++; check(idle<3000) { "Encoder AAC tidak merespons" } }
                    if(frames%44100<4096) progress("Mengodekan M4A " + (frames*4*100/(wav.length()-44).coerceAtLeast(1)).coerceAtMost(100) + "%")
                }
            }
        } finally { runCatching { if(started) muxer?.stop() }; muxer?.release(); runCatching { codec.stop() }; codec.release() }
    }
}
