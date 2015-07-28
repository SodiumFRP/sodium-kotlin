package sodium.impl

import sodium.Error
import sodium.Event
import sodium.Listener
import sodium.Stream

class CellMapHandler<A, B>(
        private val out: StreamWithSend<B>,
        private val transform: (Event<A>) -> B) : (Transaction, Event<A>) -> Unit {
    private var notSent = true

    override fun invoke(tx: Transaction, event: Event<A>) {
        if (notSent) {
            notSent = false
            tx.prioritized(out.node) {
                notSent = true
                out.send(it) {
                    transform(event)
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
            currentListener = (newStream as? StreamImpl<A>)?.listenNoFire(tx, out.node, DirectToOutHandler(out))
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

            val listener = (newStream as? StreamImpl<B>)?.listenNoFire(tx, out.node, DirectToOutHandler(out))
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

class MergeHandler<A>(val out: StreamWithSend<A>, val combine: (Event<A>, Event<A>) -> A) : (Transaction, Event<A>) -> Unit {
    private var a: Event<A>? = null
    private var b: Event<A>? = null

    override fun invoke(tx: Transaction, event: Event<A>) {
        if (a == null) {
            a = event

            tx.prioritized(out.node) {
                val a = a
                val b = b
                this.a = null
                this.b = null

                if (a != null) {
                    if (b != null) {
                        out.send(it) {
                            combine(a, b)
                        }
                    } else {
                        out.send(it, a)
                    }
                }
            }
        } else {
            b = event
        }
    }
}
