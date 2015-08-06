package sodium

import sodium.impl.CellImpl
import sodium.impl.Transaction

public class CellLoop<A> : LazyCell<A>(StreamLoop<A>(), null) {

    public fun loop(a_out: Cell<A>): Cell<A> {
        Transaction.apply {
            val cell = a_out as CellImpl<A>
            val stream = stream as StreamLoop<A>
            stream.loop(cell.stream)
            lazyValue = { cell.sampleLazy(it)().value }
        }

        return a_out
    }

    override fun sampleNoTrans(): Event<A> {
        if (!(stream as StreamLoop<A>).assigned)
            throw RuntimeException("CellLoop sampled before it was looped")
        return super.sampleNoTrans()
    }
}

