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
        tx.last {
            currentListener?.unlisten()

            try {
                currentListener = (event.value as? StreamImpl<A>)?.listen(tx, out.node, DirectToOutHandler(out))
            } catch (e: Exception) {
                out.send(tx, Error<A>(e))
            }
        }
    }

    protected fun finalize() {
        currentListener?.unlisten()
    }
}
