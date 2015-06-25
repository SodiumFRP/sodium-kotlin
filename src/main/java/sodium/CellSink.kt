package sodium

public class CellSink<A>(initValue: A) : Cell<A>(initValue, StreamSink<A>()) {
    public fun send(a: A) {
        (str as StreamSink<A>).send(a)
    }
}
