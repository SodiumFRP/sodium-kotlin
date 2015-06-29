package sodium

import junit.framework.TestCase
import java.util.ArrayList
import java.util.Arrays

public class CellTester : TestCase() {
    public fun testHold() {
        val e = StreamSink<Int>()
        val b = e.hold(0)
        val out = ArrayList<Int>()
        val l = Operational.updates(b).listen { out.add(it) }
        e.send(2)
        e.send(9)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(2, 9), out)
    }

    public fun testSnapshot() {
        val b = CellSink(0)
        val trigger = StreamSink<Long>()
        val out = ArrayList<String>()
        val l = trigger.snapshot(b) { x, y ->
            "$x $y"
        }.listen { out.add(it) }
        trigger.send(100L)
        b.send(2)
        trigger.send(200L)
        b.send(9)
        b.send(1)
        trigger.send(300L)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("100 0", "200 2", "300 1"), out)
    }

    public fun testValues() {
        val b = CellSink(9)
        val out = ArrayList<Int>()
        val l = b.listen { out.add(it) }
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9, 2, 7), out)
    }

    public fun testConstantBehavior() {
        val b = Cell(12)
        val out = ArrayList<Int>()
        val l = b.listen { out.add(it) }
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(12), out)
    }

    public fun testValueThenMap() {
        val b = CellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(b).map { it + 100 }.listen { out.add(it) }
        }
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(109, 102, 107), out)
    }

    /**
     * This is used for tests where value() produces a single initial value on listen,
     * and then we double that up by causing that single initial event to be repeated.
     * This needs testing separately, because the code must be done carefully to achieve
     * this.
     */
    private fun doubleUp(ev: Stream<Int>): Stream<Int> {
        return ev.merge(ev)
    }

    public fun testValuesTwiceThenMap() {
        val b = CellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            doubleUp(Operational.value(b)).map { it + 100 }.listen { out.add(it) }
        }
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(109, 109, 102, 102, 107, 107), out)
    }

    public fun testValuesThenCoalesce() {
        val b = CellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(b).coalesce { fst, snd ->
                snd
            }.listen { out.add(it) }
        }
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9, 2, 7), out)
    }

    public fun testValuesThenSnapshot() {
        val bi = CellSink(9)
        val bc = CellSink('a')
        val out = ArrayList<Char>()
        val l = Transaction.apply {
            Operational.value(bi).snapshot(bc).listen { out.add(it) }
        }
        bc.send('b')
        bi.send(2)
        bc.send('c')
        bi.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList('a', 'b', 'c'), out)
    }

    public fun testValuesTwiceThenSnapshot() {
        val bi = CellSink(9)
        val bc = CellSink('a')
        val out = ArrayList<Char>()
        val l = Transaction.apply {
            doubleUp(Operational.value(bi)).snapshot(bc).listen { out.add(it) }
        }
        bc.send('b')
        bi.send(2)
        bc.send('c')
        bi.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList('a', 'a', 'b', 'b', 'c', 'c'), out)
    }
}
