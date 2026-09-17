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
    val layers: List<AudioLayer> = listOf(AudioLayer(name = "Murotal"), AudioLayer(name = "Suara Alam")),
    val updatedAt: Long = System.currentTimeMillis()
)
