package sodium

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
        unsafeAddCleanup(ea_out.listen_(node) { trans, value ->
            send(trans, value)
        })
    }
}

