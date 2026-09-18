package id.umarflab.murotalaudioeditor

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectStore(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val autosaveFile get() = AtomicFile(File(context.filesDir, "autosave.mae"))
    @Synchronized fun save(project: EditorProject) {
        val file = autosaveFile
        val out = file.startWrite()
        try { out.write(json.encodeToString(project).toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun load(): EditorProject? = runCatching {
        json.decodeFromString<EditorProject>(autosaveFile.openRead().bufferedReader().use { it.readText() })
    }.getOrNull()
    fun export(project: EditorProject, uri: Uri) {
        val out = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
        out.bufferedWriter().use { it.write(json.encodeToString(project)) }
    }
    fun open(uri: Uri): EditorProject {
        val input = requireNotNull(context.contentResolver.openInputStream(uri))
        val text = input.use { it.readBytesLimited(8 * 1024 * 1024).toString(Charsets.UTF_8) }
        val p = json.decodeFromString<EditorProject>(text)
        require(p.version in 1..2 && p.layers.size <= 5) { "Versi atau jumlah track tidak didukung" }
        val ids = mutableSetOf<String>()
        p.layers.forEach { l ->
            require(ids.add(l.id)) { "ID track duplikat" }
            l.clips.forEach { c ->
                require(ids.add(c.id) && c.timelineStartMs >= 0 && c.trimStartMs >= 0 && c.trimEndMs > c.trimStartMs && c.trimEndMs <= c.sourceDurationMs && c.speed in .5f..2f && c.pitchSemitones in -12f..12f && c.volume in 0f..1f) { "Data klip tidak valid" }
                require(Uri.parse(c.uri).scheme in listOf("content", "file")) { "Proyek harus memakai audio lokal" }
                context.contentResolver.openAssetFileDescriptor(Uri.parse(c.uri), "r")?.close()
                    ?: error("Audio tidak ditemukan: " + c.name)
            }
        }
        return p
    }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out=java.io.ByteArrayOutputStream(); val block=ByteArray(8192)
    while(true) { val n=read(block); if(n<0) break; require(out.size()+n<=limit) { "Proyek terlalu besar" }; out.write(block,0,n) }
    return out.toByteArray()
}
