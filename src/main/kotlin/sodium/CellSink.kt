package sodium

import sodium.impl.CellImpl

public class CellSink<A>(initValue: A) : CellImpl<A>(initValue, StreamSink<A>()) {
    public fun send(a: A) {
        (stream as StreamSink<A>).send(a)
    }
}
