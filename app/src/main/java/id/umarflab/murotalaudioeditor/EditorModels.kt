package id.umarflab.murotalaudioeditor

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val sourceDurationMs: Long,
    val timelineStartMs: Long = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = sourceDurationMs,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val pitchSemitones: Float = 0f,
    val muted: Boolean = false
) {
    val editedDurationMs: Long get() = ((trimEndMs - trimStartMs).coerceAtLeast(0) / speed).toLong()
}

@Serializable
data class AudioLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val clips: List<AudioClip> = emptyList()
)

@Serializable
data class EditorProject(
    val version: Int = 1,
    val name: String = "Proyek Baru",
    val layers: List<AudioLayer> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun AudioClip.splitAt(positionMs: Long): Pair<AudioClip, AudioClip>? {
    val offset = positionMs - timelineStartMs
    if (offset <= 0 || offset >= editedDurationMs) return null
    val sourceCut = trimStartMs + (offset * speed.toDouble()).toLong()
    if (sourceCut <= trimStartMs || sourceCut >= trimEndMs) return null
    return copy(trimEndMs = sourceCut) to copy(
        id = UUID.randomUUID().toString(), trimStartMs = sourceCut,
        timelineStartMs = positionMs
    )
}
