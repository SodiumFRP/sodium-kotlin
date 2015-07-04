package sodium

import sodium.impl.StreamImpl
import sodium.impl.StreamWithSend
import sodium.impl.Transaction

public class StreamLoop<A> : StreamWithSend<A>() {
    var assigned: Boolean = false

    init {
        if (Transaction.getCurrent() == null)
            throw AssertionError("StreamLoop/CellLoop must be used within an explicit transaction")
    }

    public fun loop(ea_out: Stream<A>) {
        if (assigned)
            throw AssertionError("StreamLoop looped more than once")
        assigned = true
        val listener = Transaction.apply2 {
            (ea_out as StreamImpl<A>).listen(node, it, false) { trans, value ->
                send(trans, value)
            }
        }
        unsafeAddCleanup(listener)
    }
}

