package sodium.impl

import sodium.*
import sodium.Error
import sodium.Stream

class CoalesceHandler<A>(
        private val transformation: (Event<A>, Event<A>) -> A,
        private val out: StreamWithSend<A>) : (Transaction, Event<A>) -> Unit {
    private var accumulator: Event<A>? = null

    override fun invoke(transaction: Transaction, a: Event<A>) {
        val acc = accumulator
        if (acc != null) {
            try {
                accumulator = Value(transformation(acc, a))
            } catch (e: Exception) {
                accumulator = Error(e)
            }
        } else {
            transaction.prioritized(out.node) {
                val acc1 = accumulator
                if (acc1 != null) {
                    out.send(it, acc1)
                    accumulator = null
                }
            }
            accumulator = a
        }
    }
}

class LastOnlyHandler<A>(
        private val out: StreamWithSend<A>,
        private val firings: List<Event<A>>) : (Transaction, Event<A>) -> Unit {
    private var notSent = true

    override fun invoke(tx: Transaction, event: Event<A>) {
        if (notSent) {
            notSent = false
            tx.prioritized(out.node) {
                notSent = true
                out.send(it, firings.last())
            }
        }
    }
}

class CellMapHandler<A, B>(
        private val out: StreamWithSend<B>,
        private val firings: List<Event<A>>,
        private val transform: (Event<A>) -> B) : (Transaction, Event<A>) -> Unit {
    private var notSent = true

    override fun invoke(tx: Transaction, event: Event<A>) {
        if (notSent) {
            notSent = false
            tx.prioritized(out.node) {
                notSent = true
                out.send(it) {
                    transform(firings.last())
                }
            }
        }
    }
}

class DirectToOutHandler<A>(private val out: StreamWithSend<A>) : (Transaction, Event<A>) -> Unit {
    override fun invoke(tx: Transaction, event: Event<A>) {
        out.send(tx, event)
    }
}

class FlattenHandler<A>(
        private val out: StreamWithSend<A>,
        private var currentListener: Listener? = null) : (Transaction, Event<Stream<A>?>) -> Unit {

    override fun invoke(tx: Transaction, event: Event<Stream<A>?>) {
        val newStream = try {
            event.value
        } catch (e: Exception) {
            out.send(tx, Error<A>(e))
            null
        }

        tx.last {
            currentListener?.unlisten()
            currentListener = (newStream as? StreamImpl<A>)?.listen(tx, out.node, DirectToOutHandler(out))
        }
    }

    protected fun finalize() {
        currentListener?.unlisten()
    }
}

class FlatMapHandler<A, B>(
        private val out: StreamWithSend<B>,
        private val transform: (Event<A>) -> Stream<B>?,
        private var currentListener: Listener? = null) : (Transaction, Event<A>) -> Unit {

    override fun invoke(tx: Transaction, event: Event<A>) {
        val newStream = try {
            transform(event)
        } catch (e: Exception) {
            out.send(tx, Error<B>(e))
            null
        }

        tx.last {
            currentListener?.unlisten()

            val listener = (newStream as? StreamImpl<B>)?.listen(tx, out.node, DirectToOutHandler(out))
            currentListener = listener

            val debugCollector = debugCollector
            if (debugCollector != null && listener != null) {
                debugCollector.visitPrimitive(listener)
            }
        }
    }

    protected fun finalize() {
        currentListener?.unlisten()
    }
}

class ChangesHandler<A>(
        private val out: StreamWithSend<A>,
        private val thiz: CellImpl<A>) : (Transaction, Event<A>) -> Unit{
    var old: Event<A>? = null

    override fun invoke(tx: Transaction, event: Event<A>) {
        if (old == null) {
            old = thiz.sampleNoTrans()
        }

        if (old != event) {
            old = event
            out.send(tx, event)
        }
    }
}
