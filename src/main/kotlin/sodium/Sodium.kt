package sodium

import sodium.impl.*

public object Sodium {
    public fun <A> const(value: A): Cell<A> = CellImpl(Value(value), NeverStreamImpl<A>())

    public fun <A> cell(value: A, stream: Stream<A>): Cell<A> = CellImpl(Value(value), stream as StreamImpl<A>)

    public fun <A> cellSink(value: A): CellSink<A> = CellSinkImpl(value)

    public fun <A> cellLoop(): CellLoop<A> = CellLoop()

    public fun <A> streamSink(): StreamSink<A> = StreamSinkImpl<A>()

    public fun <A> streamLoop(): StreamLoop<A> = StreamLoop()

    public fun <A> cellSinkLazy(value: () -> A): LazyCellSink<A> = LazyCellSink(value)

    public fun <A> just(value: A): Stream<A> {
        val sink = StreamWithSend<A>()
        Transaction.apply2 {
            sink.send(it, Value<A>(value))
        }
        return sink
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

    public val unhandledExceptions: StreamSinkImpl<Exception> = StreamSinkImpl()
}
