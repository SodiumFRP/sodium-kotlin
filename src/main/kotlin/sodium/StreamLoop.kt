package sodium

import sodium.impl.StreamImpl
import sodium.impl.StreamWithSend
import sodium.impl.Transaction
import sodium.impl.debugCollector

class StreamLoop<A> : StreamWithSend<A>() {
    var assigned: Boolean = false

    init {
        if (Transaction.getCurrent() == null)
            throw AssertionError("StreamLoop/CellLoop must be used within an explicit transaction")
    }

    fun loop(ea_out: Stream<A>): Stream<A> {
        if (assigned)
            throw AssertionError("StreamLoop looped more than once")
        assigned = true
        val listener = Transaction.apply {
            (ea_out as StreamImpl<A>).listen(it, node) { trans, value ->
                send(trans, value)
            }
        }
        debugCollector?.visitPrimitive(listener)
        addCleanup(listener)

        return ea_out
    }
}

