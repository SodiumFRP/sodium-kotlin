package sodium.impl

import sodium.Error
import sodium.Event
import sodium.StreamSink
import sodium.Value

public class StreamSinkImpl<A> : StreamSink<A>, StreamWithSend<A>() {
    init {
        node.debugInfo = DebugInfo()
    }

    override fun send(a: A) {
        Transaction.apply2 {
            if (Transaction.inCallback > 0)
                throw IllegalStateException("You are not allowed to use send() inside a Sodium callback")
            send(it, Value(a))
        }
    }

    override fun send(a: Event<A>) {
        Transaction.apply2 {
            if (Transaction.inCallback > 0)
                throw IllegalStateException("You are not allowed to use send() inside a Sodium callback")
            send(it, a)
        }
    }

    override fun sendError(a: Exception) {
        Transaction.apply2 {
            if (Transaction.inCallback > 0)
                throw IllegalStateException("You are not allowed to use send() inside a Sodium callback")
            send(it, Error(a))
        }
    }
}
