package sodium

import sodium.impl.CellImpl
import sodium.impl.StreamImpl

public open class LazyCell<A>(stream: StreamImpl<A>, var lazyValue: (() -> A)?) : CellImpl<A>(null, stream) {

    override fun sampleNoTrans(): Event<A> {
        val value = value

        return if (value == null) {
            val lazyValue = lazyValue ?: throw IllegalStateException("Cell has no value!")
            val newValue = try {
                Value(lazyValue())
            } catch (e: Exception) {
                Error<A>(e)
            }
            this.value = newValue
            this.lazyValue = null
            newValue
        } else {
            value
        }
    }

    override fun setupValue() {
        super.setupValue()
        lazyValue = null
    }
}
