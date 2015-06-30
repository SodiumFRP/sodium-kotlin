package sodium

import sodium.impl.CellImpl
import sodium.impl.CellSinkImpl
import sodium.impl.StreamImpl
import sodium.impl.StreamSinkImpl

public object Sodium {
    jvmOverloads
    public fun <A> cell(value: A, stream: Stream<A> = StreamImpl<A>()): Cell<A> = CellImpl(value, stream)

    public fun <A> cellSink(value: A): CellSink<A> = CellSinkImpl(value)

    public fun <A> streamSink(): StreamSink<A> = StreamSinkImpl<A>()

    public fun <A> never(): Stream<A> = StreamImpl<A>()
}
