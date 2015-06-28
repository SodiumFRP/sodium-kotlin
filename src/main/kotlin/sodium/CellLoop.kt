package sodium

public class CellLoop<A> : LazyCell<A>(StreamLoop<A>(), null) {

    public fun loop(a_out: Cell<A>) {
        val me = this
        Transaction.apply {
            (me.stream as StreamLoop<A>).loop(a_out.updates(it))
            me.lazyValue = a_out.sampleLazy(it)
        }
    }

    override fun sampleNoTrans(): A {
        if (!(stream as StreamLoop<A>).assigned)
            throw RuntimeException("CellLoop sampled before it was looped")
        return super.sampleNoTrans()
    }
}

