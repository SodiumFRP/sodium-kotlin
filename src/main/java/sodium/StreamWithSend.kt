package sodium

import java.util.HashSet

public open class StreamWithSend<A> : Stream<A>() {

    protected fun send(trans: Transaction, a: A) {
        if (firings.isEmpty())
            trans.last {
                firings.clear()
            }
        firings.add(a)

        var listeners: HashSet<Node.Target>
        synchronized (Transaction.listenersLock) {
            listeners = HashSet(node.listeners)
        }
        for (target in node.listeners) {
            trans.prioritized(target.node) {
                Transaction.inCallback++
                try {
                    // Don't allow transactions to interfere with Sodium
                    // internals.
                    // Dereference the weak reference
                    val uta = target.action.get()
                    if (uta != null) {
                        // If it hasn't been gc'ed..., call it
                        (uta as TransactionHandler<A>).run(trans, a)
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
