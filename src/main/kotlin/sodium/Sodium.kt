package sodium

import sodium.impl.*

public object Sodium {
    public fun <A> const(value: A): Cell<A> = CellImpl(Value(value), NeverStreamImpl<A>())

    public fun <A> cell(value: A, stream: Stream<A>): Cell<A> = CellImpl(Value(value), stream as StreamImpl<A>)

    public fun <A> cellSink(value: A): CellSink<A> = CellSinkImpl(value)

    public fun <A> cellLoop(): CellLoop<A> = CellLoop()

    public fun <A> streamSink(): StreamSink<A> = StreamSinkImpl<A>()

    /**
     * An event that never fires.
     */
    public fun <A> never(): Stream<A> = NeverStreamImpl<A>()

    public inline fun <R> tx(body: Sodium.() -> R): R {
        return Transaction.apply2 {
            body()
        }
    }

}
