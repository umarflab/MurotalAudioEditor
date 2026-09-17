package id.umarflab.murotalaudioeditor
import org.junit.Assert.*
import org.junit.Test

class ClipTest {
    @Test fun splitPreservesSourceAndSettingsAtDoubleSpeed() {
        val clip = AudioClip(uri="test", name="Audio", sourceDurationMs=20000,
            timelineStartMs=5000, trimStartMs=2000, trimEndMs=18000, speed=2f, pitchSemitones=3f)
        val (left, right) = clip.splitAt(8000)!!
        assertEquals(8000L, left.trimEndMs)
        assertEquals(left.trimEndMs, right.trimStartMs)
        assertEquals(8000L, right.timelineStartMs)
        assertEquals(clip.editedDurationMs, left.editedDurationMs + right.editedDurationMs)
        assertEquals(clip.pitchSemitones, right.pitchSemitones)
        assertNotEquals(left.id, right.id)
    }
    @Test fun rejectsCutsOutsideAndOnEdges() {
        val clip = AudioClip(uri="test", name="Audio", sourceDurationMs=10000, timelineStartMs=2000)
        for (p in listOf(0L, 2000L, 12000L, 13000L)) assertNull(clip.splitAt(p))
    }
}
