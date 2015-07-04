package sodium

import sodium.impl.CellImpl
import sodium.impl.Transaction

public object Operational {
    /**
     * A stream that gives the updates for the cell.

     * This is an OPERATIONAL primitive, which is not part of the main Sodium
     * API. It breaks the property of non-detectability of cell steps/updates.
     * The rule with this primitive is that you should only use it in functions
     * that do not allow the caller to detect the cell updates.
     */
    public fun <A> updates(c: Cell<A>): Stream<A> {
        return Transaction.apply2 {
            (c as CellImpl<A>).updates
        }
    }

    /**
     * A stream that is guaranteed to fire once when you listen to it, giving
     * the current value of the cell, and thereafter behaves like updates(),
     * firing for each update to the cell's value.

     * This is an OPERATIONAL primitive, which is not part of the main Sodium
     * API. It breaks the property of non-detectability of cell steps/updates.
     * The rule with this primitive is that you should only use it in functions
     * that do not allow the caller to detect the cell updates.
     */
    public fun <A> value(c: Cell<A>): Stream<A> {
        return Transaction.apply2 {
            (c as CellImpl<A>).value(it)
        }
    }
}
