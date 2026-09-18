package id.umarflab.murotalaudioeditor

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File

class ExportService : Service() {
    companion object {
        val busy=MutableStateFlow(false)
        val status=MutableStateFlow("")
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var task: Job?=null
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action=="cancel") { task?.cancel(); return START_NOT_STICKY }
        if(task?.isActive==true) return START_NOT_STICKY
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("export","Ekspor audio",NotificationManager.IMPORTANCE_LOW))
        val notification=Notification.Builder(this,"export").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mengekspor audio").setContentText("Proses berjalan di latar belakang").setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=29) startForeground(11,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(11,notification)
        busy.value=true
        task=scope.launch {
            var snapshot: File?=null
            try {
                requireNotNull(intent)
                snapshot=File(requireNotNull(intent.getStringExtra("snapshot")))
                val project=Json.decodeFromString<EditorProject>(snapshot.readText())
                val uri=Uri.parse(requireNotNull(intent.getStringExtra("uri")))
                AudioExport(this@ExportService).render(project,uri,intent.getStringExtra("format")?:"WAV",intent.getIntExtra("bitrate",192000)) { status.value=it }
                status.value="Ekspor selesai. File audio sudah disimpan."
            } catch(e: CancellationException) { status.value="Ekspor dibatalkan. Hasil yang belum lengkap dapat dihapus." }
            catch(e: Exception) { status.value="Ekspor gagal: " + (e.message?:e.javaClass.simpleName) }
            finally { snapshot?.delete(); busy.value=false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); busy.value=false; super.onDestroy() }
}
