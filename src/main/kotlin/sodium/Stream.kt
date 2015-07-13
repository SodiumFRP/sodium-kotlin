package sodium

import java.util.concurrent.Executor

public interface Stream<out A> {
    /**
     * Listen for firings of this event. The returned Listener has an unlisten()
     * method to cause the listener to be removed. This is the observer pattern.
     */
    fun listen(action: (Event<A>) -> Unit): Listener

    /**
     * Transform the event's value according to the supplied function.
     */
    fun <B> map(transform: (Event<A>) -> B): Stream<B>

    /**
     * Push this event occurrence onto a new transaction. Same as split() but works
     * on a single value.
     */
    fun defer(): Stream<A>

    /**
     * Only keep event occurrences for which the predicate returns true.
     */
    fun filter(predicate: (Event<A>) -> Boolean): Stream<A>

    /**
     * Filter out any event occurrences whose value is a Java null pointer.
     */
    fun filterNotNull(): Stream<A>

    /**
     * Let event occurrences through only when the behavior's value is True.
     * Note that the behavior's value is as it was at the start of the transaction,
     * that is, no state changes from the current transaction are taken into account.
     */
    fun gate(predicate: Cell<Boolean>): Stream<A>

    /**
     * Transform an event with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     */
    fun <B, S> collect(initState: S, f: (Event<A>, Event<S>) -> Pair<B, S>): Stream<B>

    /**
     * Transform an event with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     */
    fun <B, S> collectLazy(initState: () -> S, transform: (Event<A>, Event<S>) -> Pair<B, S>): Stream<B>

    /**
     * Accumulate on input event, outputting the new state each time.
     */
    fun <S> accum(initState: S, transform: (Event<A>, Event<S>) -> S): Cell<S>

    /**
     * Accumulate on input event, outputting the new state each time.
     * Variant that takes a lazy initial state.
     */
    fun <S> accumLazy(initState: () -> S, transform: (Event<A>, Event<S>) -> S): Cell<S>

    /**
     * Throw away all event occurrences except for the first one.
     */
    fun once(): Stream<A>

    /**
     * Variant of snapshot that throws away the event's value and captures the behavior's.
     */
    fun <B> snapshot(cell: Cell<B>): Stream<B>

    /**
     * Sample the behavior at the time of the event firing. Note that the 'current value'
     * of the behavior that's sampled is the value as at the start of the transaction
     * before any state changes of the current transaction are applied through 'hold's.
     */
    fun <B, C> snapshot(cell: Cell<B>, transform: (Event<A>, Event<B>) -> C): Stream<C>

    /**
     * Attach a listener to this stream so that its {@link Listener#unlisten()} is invoked
     * when this stream is garbage collected. Useful for functions that initiate I/O,
     * returning the result of it through a stream.
     */
    fun addCleanup(cleanup: Listener): Stream<A>

    fun onExecutor(executor: Executor): Stream<A>
}
