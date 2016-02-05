package sodium

import sodium.impl.*

object Sodium {
    fun <A> const(value: A): Cell<A> = CellImpl(Value(value), NeverStreamImpl<A>())

    fun <A> cell(value: A, stream: Stream<A>): Cell<A> = CellImpl(Value(value), stream as StreamImpl<A>)

    fun <A> cellSink(value: A): CellSink<A> = CellSinkImpl(value)

    fun <A> cellLoop(): CellLoop<A> = CellLoop()

    fun <A> streamSink(): StreamSink<A> = StreamSinkImpl<A>()

    fun <A> streamLoop(): StreamLoop<A> = StreamLoop()

    fun <A> cellSinkLazy(value: () -> A): LazyCellSink<A> = LazyCellSink(value)

    fun <A> just(value: A): Stream<A> {
        val sink = StreamWithSend<A>()
        Transaction.apply {
            sink.send(it, Value<A>(value))
        }
        return sink
    }

    /**
     * An event that never fires.
     */
    fun <A> never(): Stream<A> = NeverStreamImpl<A>()

    fun <R> tx(body: Sodium.() -> R): R {
        return Transaction.apply {
            body()
        }
    }

    fun enableDebugMode() {
        debugCollector = DebugCollector()
    }

    @JvmField
    var unhandledExceptions: ((Exception) -> Unit)? = null
}
