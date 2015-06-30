package sodium.impl

import sodium.StreamSink
import sodium.Transaction

public class StreamSinkImpl<A> : StreamSink<A>, StreamWithSend<A>() {
    override fun send(a: A) {
        Transaction.apply2 {
            if (Transaction.inCallback > 0)
                throw RuntimeException("You are not allowed to use send() inside a Sodium callback")
            send(it, a)
        }
    }
}
