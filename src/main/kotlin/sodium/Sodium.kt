package sodium

import sodium.impl.CellImpl
import sodium.impl.StreamImpl

public object Sodium {
    jvmOverloads
    public fun <A> cell(value: A, stream: Stream<A> = StreamImpl<A>()): Cell<A> = CellImpl(value, stream)

    public fun <A> cellSink(value: A): CellSink<A> = CellSink(value)

    public fun <A> sink(): StreamSink<A> = StreamSink<A>()

    public fun <A> never(): Stream<A> = StreamImpl<A>()
}
