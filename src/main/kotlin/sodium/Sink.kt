package sodium

public interface Sink<A> {
    fun send(a: A)
    fun sendError(a: Exception)
}
