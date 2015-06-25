package sodium

public class StreamLoop<A> : StreamWithSend<A>() {
    var assigned: Boolean = false

    init {
        if (Transaction.getCurrent() == null)
            throw RuntimeException("StreamLoop/CellLoop must be used within an explicit transaction")
    }

    public fun loop(ea_out: Stream<A>) {
        if (assigned)
            throw RuntimeException("StreamLoop looped more than once")
        assigned = true
        val me = this
        unsafeAddCleanup(ea_out.listen_(node, object : TransactionHandler<A> {
            override fun run(trans: Transaction, a: A) {
                me.send(trans, a)
            }
        }))
    }
}

