package id.umarflab.murotalaudioeditor

import android.os.Bundle
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
    var targetLayer by remember { mutableIntStateOf(0) }
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
                    OutlinedButton(onClick = { targetLayer = 0; launcher.launch(arrayOf("audio/*")) }) { Text("+ Murotal") }
                    OutlinedButton(onClick = { targetLayer = 1; launcher.launch(arrayOf("audio/*")) }) { Text("+ Alam") }
                }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("Timeline", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                project.layers.forEach { LayerRow(it, selectedId, vm::select) }
                SelectedTools(project, selectedId, vm)
            }
        }
    }
}

@Composable
private fun LayerRow(layer: AudioLayer, selectedId: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(layer.name, color = Gold, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (layer.clips.isEmpty()) Box(Modifier.width(260.dp).height(72.dp).background(Panel, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("Belum ada audio", color = Color.Gray) }
            layer.clips.sortedBy { it.timelineStartMs }.forEach { clip ->
                val width = (clip.editedDurationMs / 1000f * 3f).coerceIn(120f, 320f).dp
                val color = if (clip.id == selectedId) Gold else Color(0xFF8B7040)
                Column(Modifier.width(width).height(72.dp).background(color, RoundedCornerShape(8.dp)).clickable { onSelect(clip.id) }.padding(8.dp)) {
                    Text(clip.name, color = Dark, maxLines = 1, fontWeight = FontWeight.Bold)
                    Text(formatMs(clip.editedDurationMs), color = Dark)
                    Text(if (clip.muted) "Senyap" else "Vol ${(clip.volume * 100).roundToInt()}%", color = Dark)
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
