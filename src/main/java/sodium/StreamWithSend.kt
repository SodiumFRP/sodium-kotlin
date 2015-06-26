package sodium

public open class StreamWithSend<A> : Stream<A>() {
    fun send(trans: Transaction, a: A) {
        if (firings.isEmpty())
            trans.last {
                firings.clear()
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
                    val uta = target.action.get()
                    if (uta != null) {
                        // If it hasn't been gc'ed..., call it
                        val handler = uta as TransactionHandler<A>
                        handler(trans, a)
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
