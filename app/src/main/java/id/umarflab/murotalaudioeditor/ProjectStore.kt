package id.umarflab.murotalaudioeditor

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ProjectStore(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val autosaveFile get() = File(context.filesDir, "autosave.mae")
    fun save(project: EditorProject) = autosaveFile.writeText(json.encodeToString(project))
    fun load(): EditorProject? = runCatching {
        if (!autosaveFile.exists()) null else json.decodeFromString<EditorProject>(autosaveFile.readText())
    }.getOrNull()
}
