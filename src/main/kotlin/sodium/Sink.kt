package sodium

public interface Sink<A> {
    fun send(a: A)
    fun send(a: Event<A>)
    fun sendError(a: Exception)
}
