package sodium

import sodium.impl.DebugInfo
import sodium.impl.StreamImpl
import sodium.impl.StreamWithSend
import sodium.impl.Transaction

public class StreamLoop<A> : StreamWithSend<A>() {
    var assigned: Boolean = false

    init {
        node.debugInfo = DebugInfo()
        if (Transaction.getCurrent() == null)
            throw AssertionError("StreamLoop/CellLoop must be used within an explicit transaction")
    }

    public fun loop(ea_out: Stream<A>) {
        if (assigned)
            throw AssertionError("StreamLoop looped more than once")
        assigned = true
        val listener = Transaction.apply2 {
            (ea_out as StreamImpl<A>).listen(it, node) { trans, value ->
                send(trans, value)
            }
        }
        addCleanup(listener)
    }
}

