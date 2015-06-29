package sodium

import junit.framework.TestCase
import java.util.ArrayList
import java.util.Arrays

public class StreamTester : TestCase() {
    public fun testSendStream() {
        val e = StreamSink<Int>()
        val out = ArrayList<Int>()
        val l = e.listen {
            out.add(it)
        }
        e.send(5)
        l.unlisten()
        TestCase.assertEquals(listOf(5), out)
        e.send(6)
        TestCase.assertEquals(listOf(5), out)
    }

    public fun testMap() {
        val e = StreamSink<Int>()
        val m = e.map {
            it.toString()
        }
        val out = ArrayList<String>()
        val l = m.listen {
            out.add(it)
        }
        e.send(5)
        l.unlisten()
        TestCase.assertEquals(listOf("5"), out)
    }

    public fun testMergeNonSimultaneous() {
        val e1 = StreamSink<Int>()
        val e2 = StreamSink<Int>()
        val out = ArrayList<Int>()
        val l = e1.merge(e2).listen {
            out.add(it)
        }
        e1.send(7)
        e2.send(9)
        e1.send(8)
        l.unlisten()
        TestCase.assertEquals(listOf(7, 9, 8), out)
    }

    public fun testMergeSimultaneous() {
        val e = StreamSink<Int>()
        val out = ArrayList<Int>()
        val l = e.merge(e).listen {
            out.add(it)
        }
        e.send(7)
        e.send(9)
        l.unlisten()
        TestCase.assertEquals(listOf(7, 7, 9, 9), out)
    }

    public fun testMergeLeftBias() {
        val e1 = StreamSink<String>()
        val e2 = StreamSink<String>()
        val out = ArrayList<String>()
        val l = e1.merge(e2).listen {
            out.add(it)
        }
        Transaction.apply2 {
            e1.send("left1a")
            e1.send("left1b")
            e2.send("right1a")
            e2.send("right1b")
        }
        Transaction.apply2 {
            e2.send("right2a")
            e2.send("right2b")
            e1.send("left2a")
            e1.send("left2b")
        }
        l.unlisten()
        TestCase.assertEquals(listOf(
                "left1a", "left1b", "right1a", "right1b",
                "left2a", "left2b", "right2a", "right2b"), out)
    }

    public fun testCoalesce() {
        val e1 = StreamSink<Int>()
        val e2 = StreamSink<Int>()
        val out = ArrayList<Int>()
        val l = e1
                .merge(e1.map { it * 100 }.merge(e2))
                .coalesce { a, b -> a + b }
                .listen { out.add(it) }
        e1.send(2)
        e1.send(8)
        e2.send(40)
        l.unlisten()
        TestCase.assertEquals(listOf(202, 808, 40), out)
    }

    public fun testFilter() {
        val e = StreamSink<Char>()
        val out = ArrayList<Char>()
        val l = e.filter { it.isUpperCase() }.listen { out.add(it) }
        e.send('H')
        e.send('o')
        e.send('I')
        l.unlisten()
        TestCase.assertEquals(listOf('H', 'I'), out)
    }

    public fun testFilterNotNull() {
        val e = StreamSink<String?>()
        val out = ArrayList<String>()
        val l = e.filterNotNull().listen { out.add(it) }
        e.send("tomato")
        e.send(null)
        e.send("peach")
        l.unlisten()
        TestCase.assertEquals(listOf("tomato", "peach"), out)
    }

    public fun testLoopStream() {
        val ea = StreamSink<Int>()
        val ec = Transaction.apply2 {
            val eb = StreamLoop<Int>()
            val ec = ea.map { it % 10 }.merge(eb) { x, y -> x + y }
            val eb_out = ea.map { it / 10 }.filter { it != 0 }
            eb.loop(eb_out)
            ec
        }
        val out = ArrayList<Int>()
        val l = ec.listen { out.add(it) }
        ea.send(2)
        ea.send(52)
        l.unlisten()
        TestCase.assertEquals(listOf(2, 7), out)
    }

    public fun testGate() {
        val ec = StreamSink<Char>()
        val epred = CellSink(true)
        val out = ArrayList<Char>()
        val l = ec.gate(epred).listen { out.add(it) }
        ec.send('H')
        epred.send(false)
        ec.send('O')
        epred.send(true)
        ec.send('I')
        l.unlisten()
        TestCase.assertEquals(Arrays.asList('H', 'I'), out)
    }

    public fun testCollect() {
        val ea = StreamSink<Int>()
        val out = ArrayList<Int>()
        val sum = ea.collect(100) { a, s ->
            a + s to a + s
        }
        val l = sum.listen { out.add(it) }
        ea.send(5)
        ea.send(7)
        ea.send(1)
        ea.send(2)
        ea.send(3)
        l.unlisten()
        TestCase.assertEquals(Arrays.asList(105, 112, 113, 115, 118), out)
    }
}
