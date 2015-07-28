package sodium.impl

import sodium.Error
import sodium.Event
import sodium.Value

public open class StreamWithSend<A> : StreamImpl<A>() {
    override var firings: Event<A>? = null

    fun send(trans: Transaction, a: Event<A>) {
        if (firings == null) {
            firings = a

            trans.last {
                firings = null
            }
        } else {
            throw IllegalStateException("send() called more than once per transaction.")
        }

        val listeners = synchronized (Transaction.listenersLock) {
            if (node.listeners.isEmpty())
                return

            node.listeners.toTypedArray()
        }

        for (target in listeners) {
            trans.prioritized(target.node) {
                Transaction.inCallback++
                try {
                    // Don't allow transactions to interfere with Sodium
                    // internals.
                    // Dereference the weak reference
                    val action = target.action.get()
                    if (action != null) {
                        action(it, a)
                    }
                } finally {
                    Transaction.inCallback--
                }
            }
        }
    }

    inline fun send(trans: Transaction, body: () -> A) {
        try {
            send(trans, Value(body()))
        } catch (e: Exception) {
            send(trans, Error(e))
        }
    }
}
