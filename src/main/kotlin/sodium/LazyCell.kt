package sodium

import sodium.impl.CellImpl
import sodium.impl.StreamImpl

public open class LazyCell<A>(stream: StreamImpl<A>, lo: Boolean, var lazyValue: (() -> Event<A>)?) : CellImpl<A>(null, stream, lo) {

    override fun sampleNoTrans(): Event<A> {
        val value = value

        return if (value == null) {
            val lazyValue = lazyValue ?: throw IllegalStateException("Cell has no value!")
            val newValue = lazyValue()
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
