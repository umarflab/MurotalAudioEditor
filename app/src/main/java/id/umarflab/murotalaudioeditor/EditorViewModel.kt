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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)

    private val _project = MutableStateFlow((store.load() ?: EditorProject()).let { p -> p.copy(version = 2, layers = (if (p.version < 2) p.layers.filter { it.clips.isNotEmpty() } else p.layers).mapIndexed { i, l -> l.copy(name = "Track " + (i + 1)) }) })
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
    fun moveSelected(targetId: String) {
        val clip = findSelected() ?: return
        stop()
        update { it.moveClipTo(clip.id, targetId) }
    }
    fun deleteTrack(id: String) {
        val layer = _project.value.layers.firstOrNull { it.id == id } ?: return
        stop()
        if (layer.clips.any { it.id == _selectedClipId.value }) _selectedClipId.value = null
        update { it.removeTrack(id) }
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

    fun importUris(uris: List<Uri>, targetId: String?) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val clips = withContext(Dispatchers.IO) { uris.mapNotNull(::readClip) }
            if (clips.isEmpty()) { message.value = "Audio tidak dapat dibaca."; return@launch }
            if (targetId != null && _project.value.layers.none { it.id == targetId }) {
                message.value = "Track tujuan sudah dihapus. Pilih track lagi."; return@launch
            }
            if (targetId == null && _project.value.layers.size >= 5) {
                message.value = "Maksimal 5 track. Pilih track yang tersedia."; return@launch
            }
            update { project ->
                val destination = targetId ?: UUID.randomUUID().toString()
                val layers = if (targetId == null) project.layers + AudioLayer(id = destination, name = "Track " + (project.layers.size + 1)) else project.layers
                project.copy(layers = layers.map { if (it.id == destination) it.appendClips(clips) else it })
            }
            message.value = "Audio ditambahkan setelah klip terakhir."
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
        layer.appendClips(listOf(copy))
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

    private val transport by lazy { TimelinePlayer(getApplication(), viewModelScope,
        { playhead.value = it }, { _isPlaying.value = it }, { message.value = it }) }
    fun playAll() {
        val clips = _project.value.layers.flatMap { it.clips }
        val end = clips.maxOfOrNull { it.timelineStartMs + it.editedDurationMs } ?: return
        transport.play(clips, if(playhead.value >= end) 0 else playhead.value, end)
    }
    fun playSelected() {
        val id = _selectedClipId.value ?: run { message.value="Pilih klip pada track terlebih dahulu."; return }
        val clips = _project.value.layers.firstOrNull { l -> l.clips.any { it.id==id } }?.clips ?: return
        val end = clips.maxOfOrNull { it.timelineStartMs + it.editedDurationMs } ?: return
        transport.play(clips, if(playhead.value >= end) 0 else playhead.value, end)
    }
    fun stop() { transport.stop() }
    fun save() { store.save(_project.value); message.value="Autosave tersimpan." }
    val projectBusy = MutableStateFlow(false)
    fun saveProject(uri: Uri) {
        val snapshot = _project.value
        viewModelScope.launch {
            projectBusy.value=true
            try { withContext(Dispatchers.IO) { store.export(snapshot,uri) }; message.value="Proyek berhasil disimpan. Audio sumber tetap harus tersedia." }
            catch(e: Exception) { message.value="Gagal menyimpan: " + e.message }
            finally { projectBusy.value=false }
        }
    }
    fun openProject(uri: Uri) {
        viewModelScope.launch {
            projectBusy.value=true
            try {
                val loaded = withContext(Dispatchers.IO) { store.open(uri) }
                stop(); _selectedClipId.value=null; playhead.value=0; update { loaded }
                message.value="Proyek berhasil dibuka."
            } catch(e: Exception) { message.value="Gagal membuka proyek: " + e.message }
            finally { projectBusy.value=false }
        }
    }
    fun exportAudio(uri: Uri, format: String, bitrate: Int) {
        if(ExportService.busy.value) return
        stop()
        val snapshot=File(getApplication<Application>().cacheDir,"export-"+UUID.randomUUID()+".json")
        snapshot.writeText(Json.encodeToString(_project.value))
        val intent=Intent(getApplication(),ExportService::class.java)
            .putExtra("snapshot",snapshot.path).putExtra("uri",uri.toString()).putExtra("format",format).putExtra("bitrate",bitrate)
        try { getApplication<Application>().startForegroundService(intent) }
        catch(e: Exception) { snapshot.delete(); message.value="Ekspor tidak dapat dimulai: " + e.message }
    }
    fun cancelExport() { getApplication<Application>().startService(Intent(getApplication(),ExportService::class.java).setAction("cancel")) }
    fun dragClip(id: String, destination: String, startMs: Long) {
        stop()
        update { it.placeClip(id,destination,startMs) }
    }

    private fun findSelected(): AudioClip? {
        val id = _selectedClipId.value ?: return null
        return _project.value.layers.flatMap { it.clips }.firstOrNull { it.id == id }
    }
    private fun editSelected(block: (AudioClip) -> AudioClip) = transformSelected { selected, layer ->
        layer.copy(clips = layer.clips.map { if (it.id == selected.id) block(it) else it })
    }
    private fun transformSelected(block: (AudioClip, AudioLayer) -> AudioLayer) {
        stop()
        val id = _selectedClipId.value ?: return
        update { project -> project.copy(layers = project.layers.map { layer ->
            layer.clips.firstOrNull { it.id == id }?.let { block(it, layer) } ?: layer
        }) }
    }
    private fun update(block: (EditorProject) -> EditorProject) {
        _project.value = block(_project.value).copy(version = 2, updatedAt = System.currentTimeMillis())
        store.save(_project.value)
    }
    override fun onCleared() { transport.stop(); super.onCleared() }
}

