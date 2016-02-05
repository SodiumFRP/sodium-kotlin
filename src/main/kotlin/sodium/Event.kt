package sodium

interface Event<out T> {
    val value: T
}

data class Value<out T>(override val value: T) : Event<T>

data class Error<out T>(private val exception: Exception) : Event<T> {
    override val value: T
        get() = throw exception
}
