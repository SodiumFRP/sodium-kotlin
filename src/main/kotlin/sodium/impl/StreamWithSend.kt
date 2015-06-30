package sodium.impl

import sodium.Transaction

public open class StreamWithSend<A> : StreamImpl<A>() {
    fun send(trans: Transaction, a: A) {
        if (firings.isEmpty()) {
            trans.last {
                firings.clear()
            }
        }

        firings.add(a)

        val listeners = synchronized (Transaction.listenersLock) {
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

}
