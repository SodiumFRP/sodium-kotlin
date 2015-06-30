package sodium

public interface Sink<A> {
    fun send(a: A)
}
