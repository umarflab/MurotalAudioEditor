package id.umarflab.murotalaudioeditor

import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
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

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<EditorViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EditorScreen(viewModel) }
    }
}

private val Dark = Color(0xFF15130D)
private val Panel = Color(0xFF252117)
private val Gold = Color(0xFFF0BE62)

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val project by vm.project.collectAsState()
    val selectedId by vm.selectedClipId.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    var targetLayer by remember { mutableIntStateOf(-1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importUris(uris, targetLayer) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, surface = Panel, background = Dark)) {
        Scaffold(containerColor = Dark, topBar = {
            Surface(color = Panel) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Murotal Audio Editor", fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::save) { Text("Simpan") }
                }
            }
        }, bottomBar = {
            Surface(color = Panel) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (isPlaying) vm.stop() else vm.playSelected() }) { Text(if (isPlaying) "Jeda" else "Putar") }
                    OutlinedButton(onClick = { targetLayer = -1; launcher.launch(arrayOf("audio/*")) }) { Text("+ Audio") }
                    OutlinedButton(onClick = vm::addTrack, enabled = project.layers.size < 5) { Text("+ Track") }
                }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("Timeline", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                Timeline(project, selectedId, vm)
                project.layers.forEachIndexed { i, layer ->
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        TextButton(onClick = { targetLayer = i; launcher.launch(arrayOf("audio/*")) }) { Text("+ Audio ke " + layer.name) }
                        TextButton(onClick = { vm.moveSelected(i) }, enabled = selectedId != null) { Text("Pindahkan ke sini") }
                    }
                }
                val message by vm.message.collectAsState()
                Text(message, Modifier.padding(horizontal = 16.dp), color = Gold)
                SelectedTools(project, selectedId, vm)
            }
        }
    }
}

@Composable
private fun Timeline(project: EditorProject, selectedId: String?, vm: EditorViewModel) {
    val position by vm.playhead.collectAsState()
    var zoom by remember { mutableFloatStateOf(1f) }
    val duration = maxOf(1000L, project.layers.flatMap { it.clips }.maxOfOrNull { it.timelineStartMs + it.editedDurationMs } ?: 1000L)
    Column(Modifier.padding(12.dp)) {
        Text("Posisi: " + formatMs(position) + " | Ketuk atau geser garis pada penggaris.")
        Row {
            TextButton(onClick = { zoom = (zoom / 2).coerceAtLeast(1f) }) { Text("Zoom −") }
            TextButton(onClick = { zoom = (zoom * 2).coerceAtMost(64f) }) { Text("Zoom +") }
            TextButton(onClick = vm::splitSelected, enabled = selectedId != null) { Text("Gunting / Split") }
        }
        if (project.layers.isEmpty()) Text("Tambahkan audio untuk membuat track pertama.")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val timelineWidth = maxWidth * zoom
            val scroll = rememberScrollState()
            Column(Modifier.horizontalScroll(scroll)) {
                Box(Modifier.width(timelineWidth).height((48 + project.layers.size * 82).dp)) {
                    Column {
                        Canvas(Modifier.width(timelineWidth).height(48.dp)
                            .pointerInput(duration, timelineWidth) {
                                detectTapGestures { vm.seek((it.x / size.width * duration).toLong().coerceIn(0, duration)) }
                            }
                            .pointerInput(duration, timelineWidth) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    vm.seek((change.position.x / size.width * duration).toLong().coerceIn(0, duration))
                                }
                            }) {
                            for (i in 0..20) {
                                val x = size.width * i / 20
                                drawLine(Color.Gray, Offset(x, 24f), Offset(x, size.height))
                            }
                        }
                        project.layers.forEach { layer ->
                            Box(Modifier.width(timelineWidth).height(82.dp).background(Panel)) {
                                layer.clips.forEach { clip ->
                                    val start = timelineWidth * (clip.timelineStartMs.toFloat() / duration)
                                    val width = timelineWidth * (clip.editedDurationMs.toFloat() / duration)
                                    Column(Modifier.offset(x = start).width(width).height(74.dp)
                                        .background(if (clip.id == selectedId) Gold else Color(0xFF8B7040), RoundedCornerShape(6.dp))
                                        .clickable { vm.select(clip.id) }.padding(4.dp)) {
                                        Text(layer.name, color = Dark, maxLines = 1)
                                        Text(clip.name, color = Dark, maxLines = 1)
                                        Text(formatMs(clip.editedDurationMs), color = Dark, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    Canvas(Modifier.matchParentSize()) {
                        val x = size.width * position.toFloat() / duration
                        drawLine(Color.Red, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                        drawCircle(Color.Red, 7.dp.toPx(), Offset(x, 12.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedTools(project: EditorProject, selectedId: String?, vm: EditorViewModel) {
    val clip = project.layers.flatMap { it.clips }.firstOrNull { it.id == selectedId }
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Column(Modifier.padding(16.dp)) {
        Text("Alat Klip", style = MaterialTheme.typography.titleMedium)
        if (clip == null) { Text("Pilih klip pada timeline untuk mengedit.", color = Color.Gray); return@Column }
        Text(clip.name, color = Gold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::duplicateSelected) { Text("Duplikasi") }
            OutlinedButton(onClick = vm::toggleMute) { Text(if (clip.muted) "Bunyikan" else "Senyap") }
            OutlinedButton(onClick = vm::deleteSelected) { Text("Hapus") }
        }
        ToolSlider("Volume", clip.volume, 0f..1f, vm::setVolume)
        ToolSlider("Speed", clip.speed, .5f..2f, vm::setSpeed)
        ToolSlider("Pitch", clip.pitchSemitones, -12f..12f, vm::setPitch)
        Text("Trim awal: ${formatMs(clip.trimStartMs)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.trimStart(-1000) }) { Text("-1 dtk") }
            OutlinedButton(onClick = { vm.trimStart(1000) }) { Text("+1 dtk") }
        }
        Text("Trim akhir: ${formatMs(clip.trimEndMs)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.trimEnd(-1000) }) { Text("-1 dtk") }
            OutlinedButton(onClick = { vm.trimEnd(1000) }) { Text("+1 dtk") }
        }
    }
}

@Composable
private fun ToolSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Text("$label: ${String.format("%.2f", value)}"); Slider(value = value, onValueChange = onChange, valueRange = range) }
}

private fun formatMs(ms: Long): String { val total = ms / 1000; return "%02d:%02d".format(total / 60, total % 60) }
