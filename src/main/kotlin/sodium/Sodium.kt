package sodium

import sodium.impl.*

public object Sodium {
    public fun <A> cell(value: A): Cell<A> = CellImpl(value, NeverStreamImpl<A>())

    public fun <A> cell(value: A, stream: Stream<A>): Cell<A> = CellImpl(value, stream as StreamImpl<A>)

    public fun <A> cellSink(value: A): CellSink<A> = CellSinkImpl(value)

    public fun <A> streamSink(): StreamSink<A> = StreamSinkImpl<A>()

    /**
     * An event that never fires.
     */
    public fun <A> never(): Stream<A> = NeverStreamImpl<A>()

    public inline fun tx(body: Sodium.() -> Unit) {
        Transaction.apply2 {
            body()
        }
    }

}
