package sodium

public interface Operational<out A> {
    /**
     * A stream that gives the updates for the cell.
     */
    fun updates(): Stream<A>

    /**
     * A stream that is guaranteed to fire once when you listen to it, giving
     * the current value of the cell, and thereafter behaves like updates(),
     * firing for each update to the cell's value.
     */
    fun value(): Stream<A>

    fun changes(): Stream<A>
}
