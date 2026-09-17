package id.umarflab.murotalaudioeditor

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val player = ExoPlayer.Builder(application).build()
    private val _project = MutableStateFlow((store.load() ?: EditorProject()).let { p -> p.copy(layers = p.layers.filter { it.clips.isNotEmpty() }.mapIndexed { i, l -> l.copy(name = "Track " + (i + 1)) }) })
    val project = _project.asStateFlow()
    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId = _selectedClipId.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    val playhead = MutableStateFlow(0L)
    val message = MutableStateFlow("")
    fun seek(ms: Long) { stop(); playhead.value = ms.coerceAtLeast(0) }
    fun addTrack() {
        if (_project.value.layers.size >= 5) { message.value = "Maksimal 5 track."; return }
        update { it.copy(layers = it.layers + AudioLayer(name = "Track " + (it.layers.size + 1))) }
    }
    fun moveSelected(target: Int) {
        val clip = findSelected() ?: return
        if (target !in _project.value.layers.indices) return
        stop()
        update { p -> p.copy(layers = p.layers.mapIndexed { i, l ->
            l.copy(clips = l.clips.filterNot { it.id == clip.id } + if (i == target) listOf(clip) else emptyList())
        }) }
    }
    fun splitSelected() {
        val clip = findSelected() ?: return
        val parts = clip.splitAt(playhead.value)
        if (parts == null) { message.value = "Letakkan garis di dalam klip yang dipilih."; return }
        stop()
        transformSelected { selected, layer ->
            layer.copy(clips = layer.clips.flatMap { if (it.id == selected.id) listOf(parts.first, parts.second) else listOf(it) })
        }
        _selectedClipId.value = parts.second.id
        message.value = "Klip terbagi. Pilih potongan untuk mengedit atau menghapus."
    }

    fun importUris(uris: List<Uri>, targetLayer: Int) {
        viewModelScope.launch {
            val clips = withContext(Dispatchers.IO) { uris.mapNotNull(::readClip) }
            if (clips.isEmpty()) { message.value = "Audio tidak dapat dibaca."; return@launch }
            update { project ->
                val layers = project.layers.toMutableList()
                if (targetLayer < 0 && layers.size < 5) layers.add(AudioLayer(name = "Track " + (layers.size + 1)))
                if (layers.isEmpty()) layers.add(AudioLayer(name = "Track 1"))
                val index = if (targetLayer < 0) layers.lastIndex else targetLayer.coerceIn(layers.indices)
                val layer = layers[index]
                var cursor = layer.clips.maxOfOrNull { it.timelineStartMs + it.editedDurationMs } ?: 0L
                val positioned = clips.map { clip -> clip.copy(timelineStartMs = cursor).also { cursor += it.editedDurationMs } }
                layers[index] = layer.copy(clips = layer.clips + positioned)
                project.copy(layers = layers)
            }
        }
    }

    private fun readClip(uri: Uri): AudioClip? = runCatching {
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Audio"
        } ?: "Audio"
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(getApplication(), uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        require(duration >= 100) { "Audio terlalu pendek" }
        AudioClip(uri = uri.toString(), name = name, sourceDurationMs = duration)
    }.getOrNull()

    fun select(id: String) { _selectedClipId.value = id }
    fun duplicateSelected() = transformSelected { selected, layer ->
        val copy = selected.copy(id = UUID.randomUUID().toString(), timelineStartMs = selected.timelineStartMs + selected.editedDurationMs)
        layer.copy(clips = layer.clips + copy)
    }
    fun deleteSelected() {
        val id = _selectedClipId.value ?: return
        update { it.copy(layers = it.layers.map { layer -> layer.copy(clips = layer.clips.filterNot { clip -> clip.id == id }) }) }
        _selectedClipId.value = null
        stop()
    }
    fun toggleMute() = editSelected { it.copy(muted = !it.muted) }
    fun setVolume(value: Float) = editSelected { it.copy(volume = value.coerceIn(0f, 1f)) }
    fun setSpeed(value: Float) = editSelected { it.copy(speed = value.coerceIn(.5f, 2f)) }
    fun setPitch(value: Float) = editSelected { it.copy(pitchSemitones = value.coerceIn(-12f, 12f)) }
    fun trimStart(deltaMs: Long) = editSelected { it.copy(trimStartMs = (it.trimStartMs + deltaMs).coerceIn(0, it.trimEndMs - 100)) }
    fun trimEnd(deltaMs: Long) = editSelected { it.copy(trimEndMs = (it.trimEndMs + deltaMs).coerceIn(it.trimStartMs + 100, it.sourceDurationMs)) }

    fun playSelected() {
        val clip = findSelected() ?: return
        player.setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.trimStartMs).setEndPositionMs(clip.trimEndMs).build()
        ).build())
        player.volume = if (clip.muted) 0f else clip.volume
        player.playbackParameters = PlaybackParameters(clip.speed, Math.pow(2.0, (clip.pitchSemitones / 12.0).toDouble()).toFloat())
        player.prepare()
        player.play()
        _isPlaying.value = true
    }
    fun stop() { player.pause(); _isPlaying.value = false }
    fun save() = store.save(_project.value)

    private fun findSelected(): AudioClip? {
        val id = _selectedClipId.value ?: return null
        return _project.value.layers.flatMap { it.clips }.firstOrNull { it.id == id }
    }
    private fun editSelected(block: (AudioClip) -> AudioClip) = transformSelected { selected, layer ->
        layer.copy(clips = layer.clips.map { if (it.id == selected.id) block(it) else it })
    }
    private fun transformSelected(block: (AudioClip, AudioLayer) -> AudioLayer) {
        val id = _selectedClipId.value ?: return
        update { project -> project.copy(layers = project.layers.map { layer ->
            layer.clips.firstOrNull { it.id == id }?.let { block(it, layer) } ?: layer
        }) }
    }
    private fun update(block: (EditorProject) -> EditorProject) {
        _project.value = block(_project.value).copy(updatedAt = System.currentTimeMillis())
        store.save(_project.value)
    }
    override fun onCleared() { player.release(); super.onCleared() }
}
