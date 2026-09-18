package id.umarflab.murotalaudioeditor
import org.junit.Assert.*
import org.junit.Test
class TrackTest {
    private fun clip(id: String, start: Long = 0) = AudioClip(id=id, uri=id, name=id, sourceDurationMs=10000, timelineStartMs=start)
    @Test fun appendUsesLatestEndAndPreservesExistingClips() {
        val old = listOf(clip("a", 20000), clip("b"))
        val result = AudioLayer(name="Track 1", clips=old).appendClips(listOf(clip("c"), clip("d")))
        assertEquals(old, result.clips.take(2))
        assertEquals(listOf(30000L,40000L), result.clips.drop(2).map { it.timelineStartMs })
    }
    @Test fun movingPreservesBothClipsAndDoesNotOverlap() {
        val source=AudioLayer(id="s",name="Track 1",clips=listOf(clip("a")))
        val dest=AudioLayer(id="d",name="Track 2",clips=listOf(clip("b")))
        val p=EditorProject(layers=listOf(source,dest))
        val moved=p.moveClipTo("a","d")
        assertTrue(moved.layers[0].clips.isEmpty())
        assertEquals(listOf("b","a"),moved.layers[1].clips.map { it.id })
        assertEquals(10000L,moved.layers[1].clips[1].timelineStartMs)
        assertEquals(moved,moved.moveClipTo("a","d"))
        assertEquals(p,p.moveClipTo("a","missing"))
    }
    @Test fun deletingTrackKeepsOtherIdsAndRenumbers() {
        val a=AudioLayer(id="a",name="Track 1")
        val b=AudioLayer(id="b",name="Track 2",clips=listOf(clip("b")))
        val result=EditorProject(layers=listOf(a,b)).removeTrack("a")
        assertEquals(b.copy(name="Track 1"),result.layers.single())
        assertTrue(result.removeTrack("b").layers.isEmpty())
    }
}