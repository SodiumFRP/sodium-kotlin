package sodium.impl

import sodium.Error
import sodium.Event
import sodium.StreamSink
import sodium.Value

class StreamSinkImpl<A> : StreamSink<A>, StreamWithSend<A>() {
    override fun send(a: A) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw AssertionError("You are not allowed to use send() inside a Sodium callback")
            send(it, Value(a))
        }
    }

    override fun send(a: Event<A>) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw AssertionError("You are not allowed to use send() inside a Sodium callback")
            send(it, a)
        }
    }

    override fun sendError(a: Exception) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw AssertionError("You are not allowed to use send() inside a Sodium callback")
            send(it, Error(a))
        }
    }
}
