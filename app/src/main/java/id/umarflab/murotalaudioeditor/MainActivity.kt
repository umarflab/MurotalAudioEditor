package id.umarflab.murotalaudioeditor

import android.os.Bundle
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.nativeCanvas

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<EditorViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(0xFF252117.toInt()),navigationBarStyle=SystemBarStyle.dark(0xFF252117.toInt()))
        setContent { EditorScreen(viewModel) }
    }
}

private val Dark = Color(0xFF15130D)
private val Panel = Color(0xFF252117)
private val Gold = Color(0xFFF0BE62)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val project by vm.project.collectAsState()
    val selectedId by vm.selectedClipId.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val projectBusy by vm.projectBusy.collectAsState()
    val exportBusy by ExportService.busy.collectAsState()
    val exportStatus by ExportService.status.collectAsState()
    val message by vm.message.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportFormat by rememberSaveable { mutableStateOf("MP3") }
    var exportBitrate by rememberSaveable { mutableIntStateOf(192000) }
    var solo by rememberSaveable { mutableStateOf(false) }
    var targetLayer by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseTrack by remember { mutableStateOf(false) }
    var trackMenuId by remember { mutableStateOf<String?>(null) }
    var deleteTrackId by remember { mutableStateOf<String?>(null) }
    var licenses by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(message) { if(message.isNotBlank()) snackbar.showSnackbar(message) }
    val saveLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::saveProject) }
    val openLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openProject) }
    val wavLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { it?.let { uri -> vm.exportAudio(uri,"WAV",exportBitrate) } }
    val aacLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { it?.let { uri -> vm.exportAudio(uri,"M4A",exportBitrate) } }
    val mp3Launcher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { it?.let { uri -> vm.exportAudio(uri,"MP3",exportBitrate) } }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it,targetLayer) }
    val type=Typography(
        bodyLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp), bodyMedium=TextStyle(fontSize=13.sp,lineHeight=18.sp),
        bodySmall=TextStyle(fontSize=11.sp,lineHeight=16.sp), labelLarge=TextStyle(fontSize=12.sp,fontWeight=FontWeight.Medium),
        titleLarge=TextStyle(fontSize=19.sp,fontWeight=FontWeight.Medium), titleMedium=TextStyle(fontSize=14.sp,fontWeight=FontWeight.Medium))
    MaterialTheme(colorScheme=darkColorScheme(primary=Gold,onPrimary=Dark,secondary=Gold,secondaryContainer=Color(0xFF514126),onSecondaryContainer=Color(0xFFE6DDCD),surface=Panel,background=Dark),typography=type) {
        if(exportDialog) ModalBottomSheet(onDismissRequest={exportDialog=false},containerColor=Panel) {
            Column(Modifier.padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Ekspor audio",style=MaterialTheme.typography.titleLarge)
                Text("Format",color=Color.LightGray)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("MP3","M4A","WAV").forEach { format ->
                        FilterChip(selected=exportFormat==format,onClick={exportFormat=format},label={Text(format)})
                    }
                }
                if(exportFormat!="WAV") {
                    Text("Kualitas",color=Color.LightGray)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf(128000 to "Standar",192000 to "Tinggi",320000 to "Terbaik").forEach { (rate,label) ->
                            FilterChip(modifier=Modifier.weight(1f),selected=exportBitrate==rate,onClick={exportBitrate=rate},label={
                                Column { Text(label); Text("${rate/1000} kbps",style=MaterialTheme.typography.bodySmall) }
                            })
                        }
                    }
                } else Text("PCM 16-bit · Stereo · 44,1 kHz",style=MaterialTheme.typography.bodyMedium)
                Button(modifier=Modifier.fillMaxWidth(),onClick={
                    exportDialog=false
                    when(exportFormat) {
                        "MP3" -> mp3Launcher.launch("Audio-edit.mp3")
                        "M4A" -> aacLauncher.launch("Audio-edit.m4a")
                        else -> wavLauncher.launch("Audio-edit.wav")
                    }
                }) { Text("Ekspor") }
            }
        }
        if(licenses) AlertDialog(onDismissRequest={licenses=false},title={Text("Lisensi LAME")},text={
            val notice=remember { context.assets.open("LAME-NOTICE.txt").bufferedReader().use { it.readText() }+"\n\n"+context.assets.open("LAME-LICENSE.txt").bufferedReader().use { it.readText() } }
            Text(notice,Modifier.verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)
        },confirmButton={TextButton(onClick={licenses=false}) { Text("Tutup") }})
        if(chooseTrack) AlertDialog(onDismissRequest={chooseTrack=false},title={Text("Tambah audio")},text={
            Column {
                project.layers.forEach { layer -> TextButton(onClick={targetLayer=layer.id; chooseTrack=false; launcher.launch(arrayOf("audio/*"))}) { Text(layer.name) } }
                if(project.layers.size<5) TextButton(onClick={targetLayer=null; chooseTrack=false; launcher.launch(arrayOf("audio/*"))}) { Text("+ Track baru") }
            }
        },confirmButton={TextButton(onClick={chooseTrack=false}) { Text("Batal") }})
        val trackMenu=project.layers.firstOrNull { it.id==trackMenuId }
        if(trackMenu!=null) AlertDialog(onDismissRequest={trackMenuId=null},title={Text(trackMenu.name)},text={Column {
            TextButton(onClick={targetLayer=trackMenu.id; trackMenuId=null; launcher.launch(arrayOf("audio/*"))}) { Text("Tambah audio") }
            TextButton(enabled=selectedId!=null,onClick={vm.moveSelected(trackMenu.id); trackMenuId=null}) { Text("Pindahkan klip ke sini") }
            TextButton(onClick={
                if(trackMenu.clips.isEmpty()) vm.deleteTrack(trackMenu.id) else deleteTrackId=trackMenu.id
                trackMenuId=null
            }) { Text("Hapus track") }
        }},confirmButton={TextButton(onClick={trackMenuId=null}) { Text("Tutup") }})
        val deleting=project.layers.firstOrNull { it.id==deleteTrackId }
        if(deleting!=null) AlertDialog(onDismissRequest={deleteTrackId=null},title={Text("Hapus ${deleting.name}?")},text={Text("${deleting.clips.size} klip akan dihapus dari proyek. File asli tetap tersimpan.")},
            confirmButton={TextButton(onClick={vm.deleteTrack(deleting.id);deleteTrackId=null}) { Text("Hapus") }},dismissButton={TextButton(onClick={deleteTrackId=null}) { Text("Batal") }})
        Scaffold(containerColor=Dark,snackbarHost={SnackbarHost(snackbar)},topBar={
            Surface(color=Panel) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"Menu proyek") }
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text("Simpan proyek")},enabled=!projectBusy,onClick={menu=false;saveLauncher.launch("Proyek-audio.mae")})
                            DropdownMenuItem(text={Text("Buka proyek")},enabled=!projectBusy && !exportBusy,onClick={menu=false;openLauncher.launch(arrayOf("application/json","application/octet-stream","*/*"))})
                            DropdownMenuItem(text={Text("Lisensi")},onClick={menu=false;licenses=true})
                        }
                    }
                    Text("Editor",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={exportDialog=true},enabled=!projectBusy && !exportBusy && project.layers.any { it.clips.isNotEmpty() }) { Text("Ekspor"); Spacer(Modifier.width(4.dp));Icon(Icons.Outlined.FileUpload,null,Modifier.size(18.dp)) }
                }
            }
        },bottomBar={
            Surface(color=Panel) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        var playbackMenu by remember { mutableStateOf(false) }
                        TextButton(onClick={playbackMenu=true}) {
                            Text(if(solo) "Track terpilih" else "Semua track");Icon(Icons.Outlined.ArrowDropDown,null)
                        }
                        DropdownMenu(expanded=playbackMenu,onDismissRequest={playbackMenu=false}) {
                            DropdownMenuItem(text={Text("Semua track")},onClick={solo=false;vm.stop();playbackMenu=false})
                            DropdownMenuItem(text={Text("Track terpilih")},onClick={solo=true;vm.stop();playbackMenu=false})
                        }
                    }
                    FilledIconButton(onClick={if(isPlaying) vm.stop() else if(solo) vm.playSelected() else vm.playAll()},enabled=!projectBusy && !exportBusy && (!solo || selectedId!=null)) {
                        Icon(if(isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(isPlaying) "Jeda" else "Putar")
                    }
                    Box {
                        var addMenu by remember { mutableStateOf(false) }
                        FilledTonalIconButton(onClick={addMenu=true},enabled=!projectBusy) { Icon(Icons.Outlined.Add,"Tambah") }
                        DropdownMenu(expanded=addMenu,onDismissRequest={addMenu=false}) {
                            DropdownMenuItem(text={Text("Tambah audio")},onClick={addMenu=false;chooseTrack=true})
                            DropdownMenuItem(text={Text("Tambah track")},enabled=project.layers.size<5,onClick={addMenu=false;vm.addTrack()})
                        }
                    }
                }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                if(projectBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(exportStatus.isNotBlank()) {
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(exportStatus,Modifier.weight(1f),color=Gold,style=MaterialTheme.typography.bodySmall)
                        IconButton(onClick={if(exportBusy) vm.cancelExport() else ExportService.status.value=""}) { Icon(Icons.Outlined.Close,if(exportBusy) "Batalkan ekspor" else "Tutup status",Modifier.size(18.dp)) }
                    }
                    if(exportBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Timeline(project,selectedId,vm,onTrackMenu={trackMenuId=it})
                SelectedTools(project,selectedId,vm)
            }
        }
    }
}

@Composable
private fun Timeline(project: EditorProject, selectedId: String?, vm: EditorViewModel, onTrackMenu: (String)->Unit) {
    val position by vm.playhead.collectAsState()
    val density=LocalDensity.current
    var zoom by remember { mutableFloatStateOf(1f) }
    val duration = maxOf(1000L, project.layers.flatMap { it.clips }.maxOfOrNull { it.timelineStartMs + it.editedDurationMs } ?: 1000L)
    Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(formatMs(position),Modifier.weight(1f).padding(start=8.dp),color=Gold,style=MaterialTheme.typography.bodyMedium)
            IconButton(onClick={zoom=(zoom/2).coerceAtLeast(1f)}) { Icon(Icons.Outlined.ZoomOut,"Perkecil timeline",Modifier.size(20.dp)) }
            IconButton(onClick={zoom=(zoom*2).coerceAtMost(64f)}) { Icon(Icons.Outlined.ZoomIn,"Perbesar timeline",Modifier.size(20.dp)) }
        }
        if(project.layers.isEmpty()) Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center) {
            Text("Belum ada audio",color=Color.Gray,style=MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(60.dp).padding(top=32.dp)) {
                project.layers.forEach { layer ->
                    Column(Modifier.height(52.dp).fillMaxWidth().clickable { onTrackMenu(layer.id) }.padding(top=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(layer.name,fontSize=10.sp,color=Color.LightGray,maxLines=1)
                        Icon(Icons.Outlined.MoreHoriz,"Atur ${layer.name}",Modifier.size(24.dp),tint=Gold)
                    }
                }
            }
        BoxWithConstraints(Modifier.weight(1f)) {
            val timelineWidth = maxWidth * zoom
            val scroll = rememberScrollState()
            Column(Modifier.horizontalScroll(scroll)) {
                Box(Modifier.width(timelineWidth).height((32 + project.layers.size * 52).dp)) {
                    Column {
                        Canvas(Modifier.width(timelineWidth).height(32.dp)
                            .pointerInput(duration, timelineWidth) {
                                detectTapGestures { vm.seek((it.x / size.width * duration).toLong().coerceIn(0, duration)) }
                            }
                            .pointerInput(duration, timelineWidth) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    vm.seek((change.position.x / size.width * duration).toLong().coerceIn(0, duration))
                                }
                            }) {
                            val divisions=maxOf(4,(size.width/80.dp.toPx()).toInt())
                            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color=android.graphics.Color.LTGRAY; textSize=9.sp.toPx()
                            }
                            for(i in 0..divisions) {
                                val x=size.width*i/divisions
                                drawLine(Color.Gray,Offset(x,20.dp.toPx()),Offset(x,size.height))
                                drawContext.canvas.nativeCanvas.drawText(formatMs(duration*i/divisions),x+3.dp.toPx(),14.dp.toPx(),paint)
                            }
                        }
                        project.layers.forEachIndexed { layerIndex, layer ->
                            Box(Modifier.width(timelineWidth).height(52.dp).background(Panel)) {

                                layer.clips.forEach { clip ->
                                    var drag by remember(clip.id) { mutableStateOf(Offset.Zero) }
                                    val widthPx=with(density) { timelineWidth.toPx() }
                                    val rowPx=with(density) { 52.dp.toPx() }
                                    val start = timelineWidth * (clip.timelineStartMs.toFloat() / duration)
                                    val width = timelineWidth * (clip.editedDurationMs.toFloat() / duration)
                                    Column(Modifier.offset(x = start).offset { IntOffset(drag.x.roundToInt(),drag.y.roundToInt()) }
                                        .zIndex(if(drag!=Offset.Zero) 2f else 0f)
                                        .width(width).height(46.dp)
                                        .pointerInput(clip.id,clip.timelineStartMs,layerIndex,widthPx,duration) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart={ vm.select(clip.id); vm.stop() },
                                                onDragCancel={ drag=Offset.Zero },
                                                onDragEnd={
                                                    val target=(layerIndex+(drag.y/rowPx).roundToInt()).coerceIn(0,project.layers.lastIndex)
                                                    val time=clip.timelineStartMs+(drag.x/widthPx*duration).toLong()
                                                    vm.dragClip(clip.id,project.layers[target].id,time)
                                                    drag=Offset.Zero
                                                },
                                                onDrag={ change,amount -> change.consume(); drag+=amount }
                                            )
                                        }
                                        .background(if (clip.id == selectedId) Gold else Color(0xFF8B7040), RoundedCornerShape(6.dp))
                                        .clickable { vm.select(clip.id) }.padding(4.dp)) {
                                        Text(clip.name, color = Dark, maxLines = 1, overflow=TextOverflow.Ellipsis, fontSize=11.sp)
                                        Text(formatMs(clip.editedDurationMs), color = Dark, maxLines = 1, fontSize=10.sp)
                                    }
                                }
                            }
                        }
                    }
                    Canvas(Modifier.matchParentSize()) {
                        val x = size.width * position.toFloat() / duration
                        drawLine(Gold, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                        drawCircle(Gold, 4.dp.toPx(), Offset(x, 12.dp.toPx()))
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectedTools(project: EditorProject, selectedId: String?, vm: EditorViewModel) {
    val clip=project.layers.flatMap { it.clips }.firstOrNull { it.id==selectedId }
    var tool by remember(selectedId) { mutableStateOf<String?>(null) }
    HorizontalDivider(Modifier.padding(top=12.dp),color=Panel)
    Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)) {
        Text(clip?.name ?: "Alat klip",color=Color.Gray,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            ToolButton(Icons.Outlined.ContentCut,"Split",clip!=null,Modifier.weight(1f),vm::splitSelected)
            ToolButton(Icons.Outlined.Crop,"Trim",clip!=null,Modifier.weight(1f)) { tool="Trim" }
            ToolButton(Icons.Outlined.ContentCopy,"Duplikasi",clip!=null,Modifier.weight(1f),vm::duplicateSelected)
            ToolButton(Icons.Outlined.DeleteOutline,"Hapus",clip!=null,Modifier.weight(1f),vm::deleteSelected)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            ToolButton(Icons.Outlined.Speed,"Speed",clip!=null,Modifier.weight(1f)) { tool="Speed" }
            ToolButton(Icons.Outlined.MusicNote,"Pitch",clip!=null,Modifier.weight(1f)) { tool="Pitch" }
            ToolButton(Icons.Outlined.VolumeUp,"Volume",clip!=null,Modifier.weight(1f)) { tool="Volume" }
            ToolButton(if(clip?.muted==true) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeMute,if(clip?.muted==true) "Bunyikan" else "Senyap",clip!=null,Modifier.weight(1f),vm::toggleMute)
        }
    }
    if(tool!=null && clip!=null) ModalBottomSheet(onDismissRequest={tool=null},containerColor=Panel) {
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=24.dp)) {
            Text(tool!!,style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            when(tool) {
                "Volume" -> ToolSlider("Volume",clip.volume,0f..1f,vm::setVolume)
                "Speed" -> ToolSlider("Speed",clip.speed,.5f..2f,vm::setSpeed)
                "Pitch" -> ToolSlider("Pitch",clip.pitchSemitones,-12f..12f,vm::setPitch)
                "Trim" -> {
                    Text("${formatMs(clip.trimStartMs)} – ${formatMs(clip.trimEndMs)}",color=Gold)
                    RangeSlider(value=clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat(),valueRange=0f..clip.sourceDurationMs.toFloat().coerceAtLeast(1f),onValueChange={ range ->
                        vm.trimStart(range.start.toLong()-clip.trimStartMs)
                        vm.trimEnd(range.endInclusive.toLong()-clip.trimEndMs)
                    })
                }
            }
            TextButton(onClick={tool=null},modifier=Modifier.align(Alignment.End)) { Text("Selesai") }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector,label: String,enabled: Boolean,modifier: Modifier,onClick: ()->Unit) {
    Column(modifier.heightIn(min=64.dp).clickable(enabled=enabled,onClick=onClick).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        val tint=if(enabled) Color(0xFFE6DDCD) else Color(0xFF615B50)
        Icon(icon,null,Modifier.size(24.dp),tint=tint)
        Text(label,color=tint,fontSize=11.sp,maxLines=1)
    }
}

@Composable
private fun ToolSlider(label: String,value: Float,range: ClosedFloatingPointRange<Float>,onChange: (Float)->Unit) {
    val display=when(label) {
        "Volume" -> "${(value*100).roundToInt()}%"
        "Speed" -> "%.2f×".format(value)
        else -> "%+.1f semiton".format(value)
    }
    Text(display,color=Gold)
    Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onChange,valueRange=range)
}

private fun formatMs(ms: Long): String {
    val total=ms/1000
    return if(total>=3600) "%d:%02d:%02d".format(total/3600,total/60%60,total%60) else "%02d:%02d".format(total/60,total%60)
}
