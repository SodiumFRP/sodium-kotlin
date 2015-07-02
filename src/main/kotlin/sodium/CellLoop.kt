package sodium

import sodium.impl.CellImpl

public class CellLoop<A> : LazyCell<A>(null, StreamLoop<A>()) {

    public fun loop(a_out: Cell<A>) {
        Transaction.apply2 {
            val cell = a_out as CellImpl<A>
            val stream = stream as StreamLoop<A>
            stream.loop(cell.updates)
            lazyValue = cell.sampleLazy(it)
        }
    }

    override fun sampleNoTrans(): A {
        if (!(stream as StreamLoop<A>).assigned)
            throw RuntimeException("CellLoop sampled before it was looped")
        return super.sampleNoTrans()
    }
}

