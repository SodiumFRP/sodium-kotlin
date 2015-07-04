package sodium.impl

import sodium.CellSink
import sodium.Error
import sodium.Transaction
import sodium.Value

public class CellSinkImpl<A>(initValue: A) : CellSink<A>, CellImpl<A>(Value(initValue), StreamWithSend<A>()) {
    override fun send(a: A) {
        Transaction.apply2 {
            if (Transaction.inCallback > 0)
                throw RuntimeException("You are not allowed to use send() inside a Sodium callback")
            (stream as StreamWithSend<A>).send(it, Value(a))
        }
    }

    override fun sendError(a: Exception) {
        Transaction.apply2 {
            if (Transaction.inCallback > 0)
                throw RuntimeException("You are not allowed to use send() inside a Sodium callback")
            (stream as StreamWithSend<A>).send(it, Error(a))
        }
    }
}
