package sodium

public class CellSink<A>(initValue: A) : Cell<A>(initValue, StreamSink<A>()) {
    public fun send(a: A) {
        (stream as StreamSink<A>).send(a)
    }
}
