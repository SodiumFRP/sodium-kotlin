package sodium

public interface Event<out T> {
    val value: T
}

public data class Value<out T>(override val value: T) : Event<T>

public data class Error<out T>(private val exception: Exception) : Event<T> {
    override val value: T
        get() = throw exception
}
