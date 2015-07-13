package sodium.impl

import sodium.*
import sodium.Error
import sodium.Stream
import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

public abstract class StreamImpl<A> : Stream<A> {
    val node = Node<A>(0)
    private val finalizers = AtomicReference<ListenerImpl>()
    abstract val firings: List<Event<A>>

    override fun listen(action: (Event<A>) -> Unit): Listener {
        val listener = Transaction.apply2 {
            listen(it, Node.NULL) { trans2, value ->
                try {
                    action(value)
                } catch (e: Exception) {
                    Sodium.unhandledExceptions.send(trans2, Value(e))
                }
            }
        }
        debugCollector?.visitPrimitive(listener)
        return listener
    }

    fun listen(trans: Transaction, target: Node<*>, action: (Transaction, Event<A>) -> Unit): Listener {
        val nodeTarget = synchronized (Transaction.listenersLock) {
            if (target.ensureBiggerThan(node.rank)) {
                trans.toRegen = true
            }

            node.link(target, action)
        }

        if (!firings.isEmpty()) {
            val firings = ArrayList(firings)
            trans.prioritized(target) {
                // Anything sent already in this transaction must be sent now so that
                // there's no order dependency between send and listen.
                for (a in firings) {
                    Transaction.inCallback++
                    try {
                        // Don't allow transactions to interfere with Sodium
                        // internals.
                        action(it, a)
                    } finally {
                        Transaction.inCallback--
                    }
                }
            }
        }
        return ListenerImplementation(this, action, nodeTarget)
    }

    override fun <B> map(transform: (Event<A>) -> B): StreamImpl<B> {
        val out = StreamWithSend<B>()
        val l = Transaction.apply2 {
            listen(it, out.node) { trans2, value ->
                out.send(trans2) {
                    transform(value)
                }
            }
        }
        debugCollector?.visitPrimitive(l)
        return out.addCleanup(l)
    }

    override fun <B> snapshot(cell: Cell<B>): StreamImpl<B> {
        val out = StreamWithSend<B>()
        val listener = Transaction.apply2 {
            listen(it, out.node) { trans2, a ->
                out.send(trans2, (cell as CellImpl<B>).sampleNoTrans())
            }
        }
        debugCollector?.visitPrimitive(listener)
        return out.addCleanup(listener)
    }

    override fun <B, C> snapshot(cell: Cell<B>, transform: (Event<A>, Event<B>) -> C): StreamImpl<C> {
        val out = StreamWithSend<C>()
        val listener = Transaction.apply2 {
            listen(it, out.node) { trans2, a ->
                out.send(trans2) {
                    transform(a, (cell as CellImpl<B>).sampleNoTrans())
                }
            }
        }
        debugCollector?.visitPrimitive(listener)
        return out.addCleanup(listener)
    }

    override fun defer(): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val listener = Transaction.apply2 {
            listen(it, out.node) { trans, a ->
                trans.post {
                    val newTrans = Transaction()
                    try {
                        out.send(newTrans, a)
                    } finally {
                        newTrans.close()
                    }
                }
            }
        }
        debugCollector?.visitPrimitive(listener)
        return out.addCleanup(listener)
    }

    fun coalesce(transaction: Transaction, combine: (Event<A>, Event<A>) -> A): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val handler = CoalesceHandler(combine, out)
        val listener = listen(transaction, out.node, handler)
        debugCollector?.visitPrimitive(listener)
        return out.addCleanup(listener)
    }

    /**
     * Clean up the output by discarding any firing other than the last one.
     */
    fun lastFiringOnly(trans: Transaction): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val listener = listen(trans, out.node, LastOnlyHandler(out, firings))
        debugCollector?.visitPrimitive(listener)
        return out.addCleanup(listener)
    }

    override fun filter(predicate: (Event<A>) -> Boolean): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val l = Transaction.apply2 {
            listen(it, out.node) { trans2, a ->
                try {
                    if (predicate(a)) {
                        out.send(trans2, a)
                    }
                } catch (e: Exception) {
                    Sodium.unhandledExceptions.send(trans2, Value(e))
                }
            }
        }
        debugCollector?.visitPrimitive(l)
        return out.addCleanup(l)
    }

    override fun filterNotNull(): StreamImpl<A> {
        return filter {
            it.value != null
        }
    }

    override fun gate(predicate: Cell<Boolean>): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val listener = Transaction.apply2 {
            listen(it, out.node) { trans2, a ->
                try {
                    if ((predicate as CellImpl<Boolean>).sampleNoTrans().value) {
                        out.send(trans2, a)
                    }
                } catch (e: Exception) {
                    Sodium.unhandledExceptions.send(trans2, Value(e))
                }
            }
        }
        debugCollector?.visitPrimitive(listener)
        return out.addCleanup(listener)
    }

    override fun <B, S> collect(initState: S, f: (Event<A>, Event<S>) -> Pair<B, S>): StreamImpl<B> {
        return collectLazy({ initState }, f)
    }

    override fun <B, S> collectLazy(initState: () -> S, transform: (Event<A>, Event<S>) -> Pair<B, S>): StreamImpl<B> {
        val eb = StreamWithSend<B>()
        val es = StreamWithSend<S>()
        val state = LazyCell(es, false) {
            try {
                Value(initState())
            } catch (e: Exception) {
                Error(e)
            }
        }

        return Transaction.apply2 {
            val listener1 = listen(it, eb.node) { trans2, a ->

            }
            val listener2 = listen(it, es.node) { trans2, a ->
                try {
                    val (b, s) = transform(a, state.sampleNoTrans())
                    es.send(trans2, Value(s))
                    eb.send(trans2, Value(b))
                } catch (e: Exception) {
                    es.send(trans2, Error(e))
                    eb.send(trans2, Error(e))
                }
            }
            debugCollector?.visitPrimitive(listener1)
            debugCollector?.visitPrimitive(listener2)
            eb.addCleanup(listener1).addCleanup(listener2)
//            val es = StreamLoop<S>()
//            val s = es.holdLazy(initState)
//            val ebs = snapshot(s, f)
//            val eb = ebs.map {
//                it.value.first
//            }
//            val es_out = ebs.map {
//                it.value.second
//            }
//            es.loop(es_out)
//            eb
        }
    }

    override fun <S> accum(initState: S, transform: (Event<A>, Event<S>) -> S): Cell<S> {
        return accumLazy({ initState }, transform)
    }

    override fun <S> accumLazy(initState: () -> S, transform: (Event<A>, Event<S>) -> S): Cell<S> {
        return Transaction.apply2 {
            val es = StreamLoop<S>()
            val s = es.holdLazy(initState)
            val es_out = snapshot(s, transform)
            es.loop(es_out)
            es_out.holdLazy(initState)
        }
    }

    override fun once(): StreamImpl<A> {
        // This is a bit long-winded but it's efficient because it deregisters
        // the listener.
        val la = arrayOfNulls<Listener>(1)
        val out = StreamWithSend<A>()
        la[0] = Transaction.apply2 {
            listen(it, out.node) { trans, a ->
                val listener = la[0]
                if (listener != null) {
                    out.send(trans, a)
                    listener.unlisten()
                    la[0] = null
                }
            }
        }
        val listener = la[0]
        return if (listener == null) this else out.addCleanup(listener)
    }

    override fun addCleanup(cleanup: Listener): StreamImpl<A> {
        (cleanup as ListenerImpl).next = finalizers.getAndSet(cleanup)
        return this
    }

    protected fun finalize() {
        var current = finalizers.getAndSet(null)
        while (current != null) {
            current.unlisten()
            current = current.next
        }
    }

    override fun onExecutor(executor: Executor): StreamImpl<A> {
        val out = StreamWithSend<A>()

        val listener = Transaction.apply2 {
            listen(it, out.node) { trans2, value ->
                executor.execute {
                    Transaction.apply2 {
                        out.send(it, value)
                    }
                }
            }
        }

        return out.addCleanup(listener)
    }
}
