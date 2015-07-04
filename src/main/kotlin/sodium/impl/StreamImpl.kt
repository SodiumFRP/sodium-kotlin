package sodium.impl

import sodium.*
import sodium.Stream
import java.util.ArrayList
import java.util.concurrent.Executor

public abstract class StreamImpl<A> : Stream<A> {
    val node = Node<A>(0)
    private val finalizers = ArrayList<Listener>()
    protected abstract val firings: List<Event<A>>

    override fun listen(action: (Event<A>) -> Unit): Listener {
        return Transaction.apply2 {
            listen(Node.NULL, it, false) { trans2, value ->
                action(value)
            }
        }
    }

    fun listen(target: Node<*>, trans: Transaction, suppressEarlierFirings: Boolean, action: (Transaction, Event<A>) -> Unit): Listener {
        val nodeTarget = synchronized (Transaction.listenersLock) {
            val (changed, nodeTarget) = node.link(target, action)
            if (changed)
                trans.toRegen = true
            nodeTarget
        }

        if (!suppressEarlierFirings && !firings.isEmpty()) {
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
        return ListenerImplementation<A>(this, action, nodeTarget)
    }

    override fun <B> map(transform: (Event<A>) -> B): StreamImpl<B> {
        val out = StreamWithSend<B>()
        val l = Transaction.apply2 {
            listen(out.node, it, false) { trans2, value ->
                out.send(trans2) {
                    transform(value)
                }
            }
        }

        return out.unsafeAddCleanup(l)
    }

    override fun <B> snapshot(beh: Cell<B>): StreamImpl<B> {
        val out = StreamWithSend<B>()
        val listener = Transaction.apply2 {
            listen(out.node, it, false) { trans2, a ->
                out.send(trans2, (beh as CellImpl<B>).sampleNoTrans())
            }
        }
        return out.unsafeAddCleanup(listener)
    }

    override fun <B, C> snapshot(b: Cell<B>, transform: (Event<A>, Event<B>) -> C): StreamImpl<C> {
        val out = StreamWithSend<C>()
        val listener = Transaction.apply2 {
            listen(out.node, it, false) { trans2, a ->
                out.send(trans2) {
                    transform(a, (b as CellImpl<B>).sampleNoTrans())
                }
            }
        }
        return out.unsafeAddCleanup(listener)
    }

    /**
     * Push this event occurrence onto a new transaction. Same as split() but works
     * on a single value.
     */
    override fun defer(): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val l1 = Transaction.apply2 {
            listen(out.node, it, false) { trans, a ->
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
        return out.unsafeAddCleanup(l1)
    }

    fun coalesce(transaction: Transaction, combine: (Event<A>, Event<A>) -> A): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val handler = CoalesceHandler(combine, out)
        val listener = listen(out.node, transaction, false, handler)
        return out.unsafeAddCleanup(listener)
    }

    /**
     * Clean up the output by discarding any firing other than the last one.
     */
    fun lastFiringOnly(trans: Transaction): StreamImpl<A> {
//        return coalesce(trans) {first, second ->
//            second
//        }

        val out = StreamWithSend<A>()
        val listener = listen(out.node, trans, false) { transaction, value ->
            transaction.prioritized(out.node) {
                out.send(it, firings.last())
            }
        }
        return out.unsafeAddCleanup(listener)
    }

    override fun filter(predicate: (Event<A>) -> Boolean): StreamImpl<A> {
        val out = StreamWithSend<A>()
        val l = Transaction.apply2 {
            listen(out.node, it, false) { trans2, a ->
                try {
                    if (predicate(a)) {
                        out.send(trans2, a)
                    }
                } catch (e: Exception) {
                    // do not send if error
                }
            }
        }
        return out.unsafeAddCleanup(l)
    }

    override fun filterNotNull(): StreamImpl<A> {
        return filter {
            it.value != null
        }
    }

    override fun gate(predicate: Cell<Boolean>): StreamImpl<A> {
//        return snapshot(predicate) { event, predicateValue ->
//            if (predicateValue) event else null
//        }.filterNotNull() as StreamImpl<A>
        val out = StreamWithSend<A>()
        val listener = Transaction.apply2 {
            listen(out.node, it, false) { trans2, a ->
                try {
                    if ((predicate as CellImpl<Boolean>).sampleNoTrans().value) {
                        out.send(trans2, a)
                    }
                } catch (e: Exception) {
                    // do not send if error
                }
            }
        }
        return out.unsafeAddCleanup(listener)
    }

    override fun <B, S> collect(initState: S, f: (Event<A>, Event<S>) -> Pair<B, S>): StreamImpl<B> {
        return collectLazy({ initState }, f)
    }

    override fun <B, S> collectLazy(initState: () -> S, f: (Event<A>, Event<S>) -> Pair<B, S>): StreamImpl<B> {
        return Transaction.apply2 {
            val es = StreamLoop<S>()
            val s = es.holdLazy(initState)
            val ebs = snapshot(s, f)
            val eb = ebs.map {
                it.value.first
            }
            val es_out = ebs.map {
                it.value.second
            }
            es.loop(es_out)
            eb
        }
    }

    override fun <S> accum(initState: S, f: (Event<A>, Event<S>) -> S): Cell<S> {
        return accumLazy({ initState }, f)
    }

    override fun <S> accumLazy(initState: () -> S, f: (Event<A>, Event<S>) -> S): Cell<S> {
        return Transaction.apply2 {
            val es = StreamLoop<S>()
            val s = es.holdLazy(initState)
            val es_out = snapshot(s, f)
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
            listen(out.node, it, false) { trans, a ->
                val listener = la[0]
                if (listener != null) {
                    out.send(trans, a)
                    listener.unlisten()
                    la[0] = null
                }
            }
        }
        val listener = la[0]
        return if (listener == null) this else out.unsafeAddCleanup(listener)
    }

    fun unsafeAddCleanup(cleanup: Listener): StreamImpl<A> {
        finalizers.add(cleanup)
        return this
    }

    protected fun finalize() {
        for (l in finalizers) {
            l.unlisten()
        }
    }

    override fun onExecutor(executor: Executor): StreamImpl<A> {
        val out = StreamWithSend<A>()

        val listener = Transaction.apply2 {
            listen(out.node, it, false) { trans2, value ->
                executor.execute {
                    Transaction.apply2 {
                        out.send(it, value)
                    }
                }
            }
        }

        return out.unsafeAddCleanup(listener)
    }
}
