package sodium

import java.util.concurrent.Executor

interface Cell<out A> {
    /**
     * Sample the cell's current value.
     *
     * This should generally be avoided in favour of value().listen(..) so you don't
     * miss any updates, but in many circumstances it makes sense.
     *
     * It can be best to use it inside an explicit transaction (using Transaction.run()).
     * For example, a b.sample() inside an explicit transaction along with a
     * b.updates().listen(..) will capture the current value and any updates without risk
     * of missing any in between.
     *
     * @throws Exception if cell containts Exception
     */
    fun sample(): Event<A>

    /**
     * A variant of sample() that works for CellLoops when they haven't been looped yet.
     */
    fun sampleLazy(): () -> Event<A>

    /**
     * Transform the cell's value according to the supplied function.
     */
    fun <B> map(transform: (Event<A>) -> B): Cell<B>

    /**
     * Listen for firings of this stream. The returned Listener has an unlisten()
     * method to cause the listener to be removed. This is the observer pattern.
     */
    fun listen(action: (Event<A>) -> Unit): Listener

    fun listen(executor: Executor, action: (Event<A>) -> Unit): Listener

    /**
     * This is an OPERATIONAL primitive, which is not part of the main Sodium
     * API. It breaks the property of non-detectability of cell steps/updates.
     * The rule with this primitive is that you should only use it in functions
     * that do not allow the caller to detect the cell updates.
     */
    fun operational(): Operational<A>
}

