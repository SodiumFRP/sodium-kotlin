package sodium.impl

class ListenerImplementation<A>(
        /**
         * It's essential that we keep the listener alive while the caller holds
         * the Listener, so that the finalizer doesn't get triggered.
         */
        private var event: StreamImpl<A>?,
        /**
         * It's also essential that we keep the action alive, since the node uses
         * a weak reference.
         */
        var action: Any?,
        private var target: Node.Target<A>?) : ListenerImpl() {

    override fun unlisten() {
        synchronized (Transaction.listenersLock) {
            val stream = event
            val node = target
            if (stream != null && node != null) {
                stream.node.unlink(node)
                event = null
                action = null
                target = null
            }
        }
    }
}
