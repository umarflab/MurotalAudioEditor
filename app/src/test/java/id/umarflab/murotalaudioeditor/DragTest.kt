package id.umarflab.murotalaudioeditor
import org.junit.Assert.*
import org.junit.Test
class DragTest {
    @Test fun collisionSnapsAndFreePositionsRemainFree() {
        val a=AudioClip(id="a",uri="a",name="a",sourceDurationMs=1000)
        val b=a.copy(id="b",timelineStartMs=2000)
        val p=EditorProject(layers=listOf(AudioLayer(id="one",name="1",clips=listOf(a)),AudioLayer(id="two",name="2",clips=listOf(b))))
        assertEquals(3000L,p.placeClip("a","two",2500).layers[1].clips.last().timelineStartMs)
        assertEquals(500L,p.placeClip("a","two",500).layers[1].clips.last().timelineStartMs)
        assertEquals(0L,p.placeClip("a","one",-500).layers[0].clips.single().timelineStartMs)
        assertEquals(p,p.placeClip("a","missing",1000))
    }
}
