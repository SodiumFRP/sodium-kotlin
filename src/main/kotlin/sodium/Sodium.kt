package sodium

import sodium.impl.*

public object Sodium {
    public fun <A> const(value: A): Cell<A> = CellImpl(Value(value), NeverStreamImpl<A>())

    public fun <A> cell(value: A, stream: Stream<A>): Cell<A> = CellImpl(Value(value), stream as StreamImpl<A>)

    public fun <A> cellSink(value: A): CellSink<A> = CellSinkImpl(value)

    public fun <A> cellLoop(): CellLoop<A> = CellLoop()

    public fun <A> streamSink(): StreamSink<A> = StreamSinkImpl<A>()

    public fun <A> streamLoop(): StreamLoop<A> = StreamLoop()

    public fun <A> lazyCell(stream: Stream<A>, value: () -> A): LazyCell<A> = LazyCell(stream as StreamImpl<A>, false) {
        try {
            Value(value())
        } catch (e: Exception) {
            Error(e)
        }
    }

    /**
     * An event that never fires.
     */
    public fun <A> never(): Stream<A> = NeverStreamImpl<A>()

    public fun <R> tx(body: Sodium.() -> R): R {
        return Transaction.apply2 {
            body()
        }
    }

    public fun enableDebugMode() {
        debugCollector = DebugCollector()
    }
}
