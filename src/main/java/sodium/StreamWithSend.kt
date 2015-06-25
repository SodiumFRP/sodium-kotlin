package sodium

import java.util.HashSet

public open class StreamWithSend<A> : Stream<A>() {
    fun send(trans: Transaction, a: A) {
        if (firings.isEmpty())
            trans.last {
                firings.clear()
            }
        firings.add(a)

        val listeners = synchronized (Transaction.listenersLock) {
            HashSet(node.listeners)
        }

        for (target in listeners) {
            trans.prioritized(target.node) {
                Transaction.inCallback++
                try {
                    // Don't allow transactions to interfere with Sodium
                    // internals.
                    // Dereference the weak reference
                    val uta = target.action.get()
                    if (uta != null) {
                        // If it hasn't been gc'ed..., call it
                        (uta as TransactionHandler<A>)(trans, a)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                } finally {
                    Transaction.inCallback--
                }
            }
        }
    }

}
