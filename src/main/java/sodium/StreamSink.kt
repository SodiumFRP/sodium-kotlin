package sodium

public class StreamSink<A> : StreamWithSend<A>() {

    public fun send(a: A) {
        Transaction.apply {
            if (Transaction.inCallback > 0)
                throw RuntimeException("You are not allowed to use send() inside a Sodium callback")
            send(it, a)
        }
    }
}
