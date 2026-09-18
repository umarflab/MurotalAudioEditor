package id.umarflab.murotalaudioeditor

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream

/** Preserve output before AGP uninstalls the tested app and removes externalFilesDir. */
internal fun preserveTestArtifacts(directory: File) {
    val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
    for(command in listOf("mkdir -p /data/local/tmp/editor-ui-checks", "cp -r ${directory.absolutePath}/. /data/local/tmp/editor-ui-checks/")) {
        automation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
}
