package sodium

import sodium.impl.StreamWithSend
import sodium.impl.Transaction

class LazyCellSink<A>(initValue: () -> A) : LazyCell<A>(StreamWithSend(), initValue), Sink<A> {
    override fun send(a: A) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw AssertionError("You are not allowed to use send() inside a Sodium callback")
            (stream as StreamWithSend<A>).send(it, Value(a))
        }
    }

    override fun send(a: Event<A>) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw AssertionError("You are not allowed to use send() inside a Sodium callback")
            (stream as StreamWithSend<A>).send(it, a)
        }
    }

    override fun sendError(a: Exception) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw AssertionError("You are not allowed to use send() inside a Sodium callback")
            (stream as StreamWithSend<A>).send(it, Error(a))
        }
    }
}
