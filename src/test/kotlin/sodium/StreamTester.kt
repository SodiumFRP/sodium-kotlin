package sodium

import junit.framework.TestCase
import sodium.impl.Transaction
import java.util.ArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

public class StreamTester : TestCase() {
    init {
        Sodium.enableDebugMode()
    }

    public fun testSendStream() {
        val e = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val l = e.listen {
            out.add(it.value)
        }
        System.gc()
        e.send(5)
        e.send(7)
        TestCase.assertEquals(listOf(5, 7), out)

        out.clear()
        System.gc()
        e.send(5)
        l.unlisten()
        e.send(6)
        TestCase.assertEquals(listOf(5), out)
    }

    public fun testListenThrows() {
        val e = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val l = e.listen {
            if (it.value % 2 == 0) {
                throw RuntimeException()
            } else {
                out.add(it.value)
            }
        }
        System.gc()
        e.send(1)
        e.send(2)
        e.send(3)
        e.send(4)
        e.send(5)
        TestCase.assertEquals(listOf(1, 3, 5), out)
        l.unlisten()
    }

    public fun testListenInTx() {
        val e = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        Sodium.tx {
            System.gc()
            e.send(5)
            val l = e.listen {
                out.add(it.value)
            }
            l.unlisten()
        }
        TestCase.assertEquals(listOf(5), out)
    }

    public fun testMap() {
        val e = Sodium.streamSink<Int>()
        val m = e.map {
            it.value.toString()
        }
        val out = ArrayList<String>()
        val l = m.listen {
            out.add(it.value)
        }
        System.gc()
        //dump(e)
        e.send(5)
        l.unlisten()
        TestCase.assertEquals(listOf("5"), out)
    }

    public fun testMapThrows() {
        val e = Sodium.streamSink<Int>()
        val m = e.map {
            val v = it.value
            if (v % 2 == 0)
                throw RuntimeException("map$v")
            v.toString()
        }
        val out = ArrayList<String>()
        val l = m.listen {
            try {
                out.add(it.value)
            } catch (e: Exception) {
                out.add(e.getMessage())
            }
        }
        System.gc()
        e.send(1)
        e.send(2)
        e.send(3)
        e.sendError(RuntimeException("sent"))
        e.send(5)
        l.unlisten()
        TestCase.assertEquals(listOf("1", "map2", "3", "sent", "5"), out)
    }

    public fun testMergeNonSimultaneous() {
        val e1 = Sodium.streamSink<Int>()
        val e2 = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val l = e1.merge(e2).listen {
            out.add(it.value)
        }
        System.gc()
        e1.send(7)
        e2.send(9)
        e1.send(8)
        l.unlisten()
        TestCase.assertEquals(listOf(7, 9, 8), out)
    }

    public fun testMergeSimultaneous() {
        val s1 = Sodium.streamSink<Int>()
        val s2 = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val l = s1.merge(s2).listen {
            out.add(it.value)
        }
        System.gc()
        Sodium.tx {
            s1.send(7)
            s2.send(60)
        }
        s1.send(9)
        Sodium.tx {
            s1.send(60)
            s2.send(90)
        }
        Sodium.tx {
            s2.send(90)
            s1.send(60)
        }
        Sodium.tx {
            s2.send(90)
            s1.send(60)
        }
        l.unlisten()
        TestCase.assertEquals(listOf(60, 9, 90, 90, 90), out)
    }

    public fun testFilter() {
        val e = Sodium.streamSink<Char>()
        val out = ArrayList<Char>()
        val l = e.filter { it.value.isUpperCase() }.listen { out.add(it.value) }
        System.gc()
        e.send('H')
        e.send('o')
        e.send('I')
        l.unlisten()
        TestCase.assertEquals(listOf('H', 'I'), out)
    }

    public fun testFilterThrows() {
        val e = Sodium.streamSink<Char?>()
        val out = ArrayList<Char>()
        val l = e.filter { it.value!!.isUpperCase() }.listen { out.add(it.value) }
        System.gc()
        e.send('H')
        e.send(null)
        e.send('o')
        e.send('I')
        l.unlisten()
        TestCase.assertEquals(listOf('H', 'I'), out)
    }

    public fun testFilterNotNull() {
        val e = Sodium.streamSink<String?>()
        val out = ArrayList<String>()
        val l = e.filterNotNull().listen { out.add(it.value) }
        System.gc()
        e.send("tomato")
        e.send(null)
        e.send("peach")
        l.unlisten()
        TestCase.assertEquals(listOf("tomato", "peach"), out)
    }

    public fun testLoopStream() {
        val ea = Sodium.streamSink<Int>()
        val ec = Transaction.apply {
            val eb = StreamLoop<Int>()
            val ec = ea.map { it.value % 10 }.merge(eb) { x, y -> x.value + y.value }
            val eb_out = ea.map { it.value / 10 }.filter { it.value != 0 }
            eb.loop(eb_out)
            ec
        }
        val out = ArrayList<Int>()
        val l = ec.listen { out.add(it.value) }
        System.gc()
        ea.send(2)
        ea.send(52)
        l.unlisten()
        TestCase.assertEquals(listOf(2, 7), out)
    }

    public fun testGate() {
        val ec = Sodium.streamSink<Char>()
        val predicate = Sodium.cellSink(true)
        val out = ArrayList<Char>()
        val l = ec.gate(predicate).listen { out.add(it.value) }
        System.gc()
        ec.send('H')
        predicate.send(false)
        ec.send('O')
        predicate.send(true)
        ec.send('I')
        l.unlisten()
        TestCase.assertEquals(listOf('H', 'I'), out)
    }

    public fun testCollect() {
        val ea = Sodium.streamSink<Int>()
        val out = ArrayList<Int>()
        val sum = ea.collect(100) { a, s ->
            a.value + s.value to a.value + s.value
        }
        val l = sum.listen { out.add(it.value) }
        System.gc()
        //dump(ea)
        ea.send(5)
        ea.send(7)
        ea.send(1)
        ea.send(2)
        ea.send(3)
        l.unlisten()
        TestCase.assertEquals(listOf(105, 112, 113, 115, 118), out)
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
        TestCase.assertEquals(listOf(100, 105, 112, 113, 115, 118), out)
    }

    public fun testOnce() {
        val e = Sodium.streamSink<Char>()
        val out = ArrayList<Char>()
        val l = e.once().listen { out.add(it.value) }
        System.gc()
        e.send('A')
        e.send('B')
        e.send('C')
        l.unlisten()
        TestCase.assertEquals(listOf('A'), out)
    }

    public fun testDefer() {
        val out = ArrayList<Char>()
        val e = Sodium.streamSink<Char>()
        val l = Sodium.tx {
            val b = e.hold(' ')
            e.defer().snapshot(b).listen { out.add(it.value) }
        }
        System.gc()
        e.send('C')
        e.send('B')
        e.send('A')
        l.unlisten()
        TestCase.assertEquals(listOf('C', 'B', 'A'), out)
    }

    public fun testOnExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        val threadId = arrayOfNulls<Long>(2)
        executor.execute {
            threadId.set(0, Thread.currentThread().getId())
        }

        val (l, s) = Sodium.tx {
            val s = streamSink<Unit>()
            s.defer(executor).listen {
                threadId.set(1, Thread.currentThread().getId())
            } to s
        }

        System.gc()
        s.send(Unit)

        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        TestCase.assertEquals(threadId[0], threadId[1])
        l.unlisten()
    }

    public fun testFlatten() {
        val out = ArrayList<Int>()
        val sink = Sodium.streamSink<Int>()
        val s1 = sink.map { it.value * 10 }
        val s2 = sink.map { it.value * 100 }
        val ss = Sodium.streamSink<Stream<Int>>()
        val l = ss.flatten().listen { out.add(it.value) }

        System.gc()

        sink.send(1)

        ss.send(s1)
        sink.send(2)
        sink.send(3)

        Sodium.tx {
            ss.send(s2)
            sink.send(4)
        }

        sink.send(5)
        sink.send(6)
        ss.send(s1)
        sink.send(7)

        l.unlisten()
        TestCase.assertEquals(listOf(20, 30, 40, 500, 600, 70), out)
    }

    public fun testFlatMap() {
        val out = ArrayList<String>()
        val sink1 = Sodium.streamSink<Int>()
        val sink2 = Sodium.streamSink<String>()
        val s1 = sink2.map { "A" + it.value }
        val s2 = sink2.map { "B" + it.value }

        val l = sink1.flatMap {
            if (it.value % 2 == 0) {
                s1
            } else {
                s2
            }
        }.listen { out.add(it.value) }

        sink2.send("a")

        sink1.send(1)
        sink2.send("b")
        sink2.send("c")

        sink1.send(2)
        sink2.send("d")
        sink2.send("e")

        Sodium.tx {
            sink1.send(3)
            sink2.send("f")
        }

        sink2.send("g")

        l.unlisten()
        TestCase.assertEquals(listOf("Bb", "Bc", "Ad", "Ae", "Af", "Bg"), out)
    }

    public fun testFlatMap2() {
        val out = ArrayList<String>()
        val sink = Sodium.streamSink<Int>()

        val l = sink.flatMap {
            Sodium.just("A" + it.value).defer()
        }.listen { out.add(it.value) }

        sink.send(1)

        Sodium.tx {
            sink.send(3)
        }

        l.unlisten()
        TestCase.assertEquals(listOf("A1", "A3"), out)
    }

    public fun testSwitchAndDefer() {
        val out = ArrayList<String>()
        val si = Sodium.streamSink<Int>()
        val l = si.map {
            Sodium.const("A" + it.value).operational().value().defer()
        }.hold(Sodium.never()).switchS().listen { out.add(it.value) }
        si.send(2);
        si.send(4);
        l.unlisten();
        TestCase.assertEquals(listOf("A2", "A4"), out);
    }

    public fun testDefer2() {
        val out = ArrayList<String>()
        val sink = Sodium.streamSink<Int>()
        val l2 = arrayOfNulls<Listener>(1)
        val l = Sodium.tx {
            sink.map {
                const("A").operational().value().defer()
            }.listen {
                l2[0] = it.value.listen {
                    out.add(it.value)
                }
            }
        }
        sink.send(1)
        l2[0]?.unlisten()
        l.unlisten();
        TestCase.assertEquals(listOf("A"), out);
    }
}
