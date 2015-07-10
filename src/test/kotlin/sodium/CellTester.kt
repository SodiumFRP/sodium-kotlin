package sodium

import junit.framework.TestCase
import sodium.impl.Node
import sodium.impl.Transaction
import java.util.ArrayList
import java.util.Arrays

public class CellTester : TestCase() {
    init {
        Sodium.enableDebugMode()
    }

    public fun testHold() {
        val e = Sodium.streamSink<Int>()
        val b = e.hold(0)
        val out = ArrayList<Int>()
        val l = Operational.updates(b).listen { out.add(it.value) }
        System.gc()
        e.send(2)
        e.send(9)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(2, 9), out)
    }

    public fun testSnapshot() {
        val b = Sodium.cellSink(0)
        val trigger = Sodium.streamSink<Long>()
        val out = ArrayList<String>()
        val l = trigger.snapshot(b) { x, y ->
            "${x.value} ${y.value}"
        }.listen { out.add(it.value) }
        System.gc()
        trigger.send(100L)
        b.send(2)
        trigger.send(200L)
        b.send(9)
        b.send(1)
        trigger.send(300L)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("100 0", "200 2", "300 1"), out)
    }

    public fun testSnapshotThrows() {
        val b = Sodium.cellSink(0)
        val trigger = Sodium.streamSink<Int>()
        val out = ArrayList<String>()
        val l = trigger.snapshot(b) { x, y ->
            if (x.value % 2 != 0)
                throw RuntimeException("ex:${x.value}")
            "${x.value} ${y.value}"
        }.listen {
            try {
                out.add(it.value)
            } catch (e: Exception) {
                out.add(e.getMessage())
            }
        }
        System.gc()
        trigger.send(100)
        b.send(2)
        trigger.send(201)
        b.send(9)
        b.send(1)
        trigger.send(300)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("100 0", "ex:201", "300 1"), out)
    }

    public fun testValues() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = b.listen { out.add(it.value) }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9, 2, 7), out)
    }

    public fun testConstantBehavior() {
        val b = Sodium.const(12)
        val out = ArrayList<Int>()
        val l = b.listen { out.add(it.value) }
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(12), out)
    }

    public fun testValueThenMap() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(b).map { it.value + 100 }.listen { out.add(it.value) }
        }
        System.gc()
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
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            doubleUp(Operational.value(b)).map { it.value + 100 }.listen { out.add(it.value) }
        }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(109, 109, 102, 102, 107, 107), out)
    }

    public fun testValuesThenCoalesce() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(b).coalesce { fst, snd ->
                snd.value
            }.listen { out.add(it.value) }
        }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9, 2, 7), out)
    }

    public fun testValuesThenSnapshot() {
        val bi = Sodium.cellSink(9)
        val bc = Sodium.cellSink('a')
        val out = ArrayList<Char>()
        val l = Transaction.apply {
            Operational.value(bi).snapshot(bc).listen { out.add(it.value) }
        }
        System.gc()
        bc.send('b')
        bi.send(2)
        bc.send('c')
        bi.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList('a', 'b', 'c'), out)
    }

    public fun testValuesTwiceThenSnapshot() {
        val bi = Sodium.cellSink(9)
        val bc = Sodium.cellSink('a')
        val out = ArrayList<Char>()
        val l = Transaction.apply {
            doubleUp(Operational.value(bi)).snapshot(bc).listen { out.add(it.value) }
        }
        System.gc()
        bc.send('b')
        bi.send(2)
        bc.send('c')
        bi.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList('a', 'a', 'b', 'b', 'c', 'c'), out)
    }

    public fun testValuesThenMerge() {
        val bi = Sodium.cellSink(9)
        val bj = Sodium.cellSink(2)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(bi).merge(Operational.value(bj)) { x, y ->
                x.value + y.value
            }.listen { out.add(it.value) }
        }
        System.gc()
        bi.send(1)
        bj.send(4)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(11, 1, 4), out)
    }

    public fun testValuesThenFilter() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(b).filter {
                true
            }.listen { out.add(it.value) }
        }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9, 2, 7), out)
    }

    public fun testValuesTwiceThenFilter() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply2 {
            doubleUp(Operational.value(b)).filter {
                true
            }.listen { out.add(it.value) }
        }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9, 9, 2, 2, 7, 7), out)
    }

    public fun testValuesThenOnce() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            Operational.value(b).once().listen { out.add(it.value) }
        }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9), out)
    }

    public fun testValuesTwiceThenOnce() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val l = Transaction.apply {
            doubleUp(Operational.value(b)).once().listen { out.add(it.value) }
        }
        System.gc()
        b.send(2)
        b.send(7)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(9), out)
    }

    public fun testValuesLateListen() {
        val b = Sodium.cellSink(9)
        val out = ArrayList<Int>()
        val value = Operational.value(b)
        System.gc()
        b.send(8)
        val l = value.listen { out.add(it.value) }
        System.gc()
        b.send(2)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(2), out)
    }

    public fun testMapB() {
        val b = Sodium.cellSink(6)
        val out = ArrayList<String>()
        val l = b.map { it.value.toString() }.listen {
            out.add(it.value)
        }
        System.gc()
        //dump(b)
        b.send(8)
        Sodium.tx {
            b.send(7)
            b.send(9)
        }
        l.unlisten()
        System.gc()
        //dump(b)
        TestCase.assertEquals(Arrays.asList("6", "8", "9"), out)
    }

    public fun testMapBLateListen() {
        val b = Sodium.cellSink(6)
        val out = ArrayList<String>()
        val bm = b.map {
            it.value.toString()
        }
        System.gc()
        b.send(2)
        val l = bm.listen { out.add(it.value) }
        System.gc()
        b.send(8)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("2", "8"), out)
    }

    public fun testTransaction() {
        val calledBack = BooleanArray(1)
        Transaction.apply {
            it.prioritized(Node.NULL) {
                calledBack[0] = true
            }
        }
        TestCase.assertEquals(true, calledBack[0])
    }

    public fun testHoldIsDelayed() {
        val e = Sodium.streamSink<Int>()
        val h = e.hold(0)
        val pair = e.snapshot(h) { a, b ->
            "${a.value} ${b.value}"
        }
        val out = ArrayList<String>()
        val l = pair.listen { out.add(it.value) }
        System.gc()
        e.send(2)
        e.send(3)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("2 0", "3 2"), out)
    }

    public fun testLoopBehavior() {
        val ea = Sodium.streamSink<Int>()
        val sum_out = Transaction.apply {
            val sum = CellLoop<Int>()
            val sum_out_ = ea.snapshot(sum) { x, y ->
                x.value + y.value
            }.hold(0)
            sum.loop(sum_out_)
            sum_out_
        }
        val out = ArrayList<Int>()
        val l = sum_out.listen { out.add(it.value) }
        System.gc()
        ea.send(2)
        ea.send(3)
        ea.send(1)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(0, 2, 5, 6), out)
        TestCase.assertEquals(6, sum_out.sample().value)
    }

    public fun testCollect() {
        val ea = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val sum = ea.hold(100).collect(0) { a, s ->
            a.value + s.value to a.value + s.value
        }
        val l = sum.listen { out.add(it.value) }
        System.gc()
        ea.send(5)
        ea.send(7)
        ea.send(1)
        ea.send(2)
        ea.send(3)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(100, 105, 112, 113, 115, 118), out)
    }

    public fun testAccum() {
        val ea = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val sum = ea.accum(100) { a, s ->
            a.value + s.value
        }
        val l = sum.listen { out.add(it.value) }
        System.gc()
        ea.send(5)
        ea.send(7)
        ea.send(1)
        ea.send(2)
        ea.send(3)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(100, 105, 112, 113, 115, 118), out)
    }

    public fun testLoopValueSnapshot() {
        val out = ArrayList<String>()
        val l = Transaction.apply {
            val a = Sodium.const("lettuce")
            val b = Sodium.cellLoop<String>()
            val eSnap = Operational.value(a).snapshot(b) { a, b ->
                "${a.value} ${b.value}"
            }
            b.loop(Sodium.const("cheese"))
            eSnap.listen { out.add(it.value) }
        }
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("lettuce cheese"), out)
    }

    public fun testLoopValueHold() {
        val out = ArrayList<String>()
        val value = Transaction.apply {
            val a = Sodium.cellLoop<String>()
            val value_ = Operational.value(a).hold("onion")
            a.loop(Sodium.const("cheese"))
            value_
        }
        val eTick = Sodium.streamSink<Unit>()
        val l = eTick.snapshot(value).listen { out.add(it.value) }
        System.gc()
        eTick.send(Unit)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList("cheese"), out)
    }

}
