package id.umarflab.murotalaudioeditor

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class EditorUiTest {
    @Test fun compactEditorAndExportSheet() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        val store=ProjectStore(context)
        val old=store.load()
        val clips=listOf("Bacaan.mp3","Hujan.mp3","Angin.mp3","Air.mp3","Burung.mp3")
        store.save(EditorProject(version=2,layers=clips.mapIndexed { i,name ->
            AudioLayer(name="Track ${i+1}",clips=listOf(AudioClip(uri="file:///unused.wav",name=name,sourceDurationMs=(i+1)*300000L)))
        }))
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
                assertTrue(device.wait(Until.hasObject(By.text("Bacaan.mp3")),10000))
                device.findObject(By.text("Bacaan.mp3")).click()
                device.waitForIdle()
                val directory=File(context.getExternalFilesDir(null),"ui-checks").apply { mkdirs() }
                device.takeScreenshot(File(directory,"editor-five-tracks.png"))
                // Small screens intentionally scroll; transport and header remain fixed.
                if(!device.hasObject(By.text("Speed"))) {
                    device.swipe(device.displayWidth/2,device.displayHeight*3/4,device.displayWidth/2,device.displayHeight/3,20)
                    device.waitForIdle()
                }
                assertTrue("Speed is reachable",device.hasObject(By.text("Speed")))
                assertTrue("Pitch is reachable",device.hasObject(By.text("Pitch")))
                assertFalse(device.hasObject(By.textContains("Tekan-tahan")))
                device.takeScreenshot(File(directory,"editor-tools.png"))
                device.findObject(By.text("Semua track")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Track terpilih")),5000))
                device.findObject(By.text("Track terpilih")).click()
                device.findObject(By.text("Ekspor")).click()
                assertTrue(device.wait(Until.hasObject(By.text("MP3")),5000))
                assertTrue(device.hasObject(By.text("128 kbps")))
                assertTrue(device.hasObject(By.text("192 kbps")))
                assertTrue(device.hasObject(By.text("320 kbps")))
                device.waitForIdle()
                device.takeScreenshot(File(directory,"export-quality.png"))
            }
        } finally {
            if(old!=null) store.save(old) else File(context.filesDir,"autosave.mae").delete()
        }
    }
}
