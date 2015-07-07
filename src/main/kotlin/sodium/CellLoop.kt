package sodium

import sodium.impl.CellImpl
import sodium.impl.Transaction

public class CellLoop<A> : LazyCell<A>(StreamLoop<A>(), true, null) {

    public fun loop(a_out: Cell<A>) {
        Transaction.apply2 {
            val cell = a_out as CellImpl<A>
            val stream = stream as StreamLoop<A>
            stream.loop(cell.updates)
            lazyValue = cell.sampleLazy(it)
        }
    }

    override fun sampleNoTrans(): Event<A> {
        if (!(stream as StreamLoop<A>).assigned)
            throw RuntimeException("CellLoop sampled before it was looped")
        return super.sampleNoTrans()
    }
}

