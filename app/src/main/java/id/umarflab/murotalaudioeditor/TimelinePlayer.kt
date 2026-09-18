package id.umarflab.murotalaudioeditor

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

/** One timeline clock; clips are prepared before the clock starts. */
class TimelinePlayer(private val context: Context, private val scope: CoroutineScope,
    private val position: (Long) -> Unit, private val state: (Boolean) -> Unit,
    private val failure: (String) -> Unit) {
    private data class Voice(val clip: AudioClip, val player: ExoPlayer)
    private var voices = mutableListOf<Voice>()
    private var generation=0
    private var job: Job? = null
    fun stop() {
        generation++; job?.cancel(); job = null
        voices.forEach { it.player.release() }; voices.clear(); state(false)
    }
    fun play(clips: List<AudioClip>, start: Long, end: Long) {
        stop()
        if (end <= start) return
        state(true)
        val session=generation
        val voices=mutableListOf<Voice>()
        this.voices=voices
        job = scope.launch {
            try {
                // At most one prepared player per simultaneously active clip. Future clips
                // are prepared shortly before their start; no full-file PCM allocation.
                var cursor = start
                var clock = SystemClock.elapsedRealtime()
                var bufferStarted=0L
                while (isActive && cursor < end) {
                    val needed = clips.filter { !it.muted && it.timelineStartMs < cursor + 2000 && it.timelineStartMs + it.editedDurationMs > cursor }
                    voices.filter { v -> needed.none { it.id == v.clip.id } }.toList().forEach { it.player.release(); voices.remove(it) }
                    needed.filter { c -> voices.none { it.clip.id == c.id } }.forEach { c ->
                        val p = ExoPlayer.Builder(context).build()
                        p.addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                failure(c.name + ": " + error.errorCodeName); stop()
                            }
                        })
                        p.setMediaItem(MediaItem.Builder().setUri(c.uri).setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.trimStartMs).setEndPositionMs(c.trimEndMs).build()).build())
                        p.playbackParameters = PlaybackParameters(c.speed, Math.pow(2.0, c.pitchSemitones / 12.0).toFloat())
                        p.volume = c.volume
                        p.seekTo(((cursor - c.timelineStartMs).coerceAtLeast(0) * c.speed).toLong())
                        voices.add(Voice(c,p)); p.prepare()
                    }
                    val active = voices.filter { cursor >= it.clip.timelineStartMs }
                    // Pause the shared clock during buffering to avoid audible track drift.
                    if (active.any { it.player.playbackState != Player.STATE_READY && it.player.playbackState != Player.STATE_ENDED }) {
                        if(bufferStarted==0L) bufferStarted=SystemClock.elapsedRealtime()
                        check(SystemClock.elapsedRealtime()-bufferStarted<30000) { "Audio terlalu lama menyiapkan pemutaran" }
                        voices.forEach { it.player.pause() }; delay(20); clock = SystemClock.elapsedRealtime(); continue
                    }
                    bufferStarted=0
                    active.forEach { v ->
                        val expected = ((cursor-v.clip.timelineStartMs)*v.clip.speed).toLong()
                        if (kotlin.math.abs(v.player.currentPosition-expected) > 150) v.player.seekTo(expected)
                        v.player.play()
                    }
                    position(cursor)
                    delay(20)
                    val now = SystemClock.elapsedRealtime(); cursor += now-clock; clock=now
                }
                position(end)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { failure("Pemutaran gagal: " + e.message) }
            finally { voices.forEach { it.player.release() }; voices.clear(); if(session==generation) state(false) }
        }
    }
}
