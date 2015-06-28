package sodium

public open class LazyCell<A>(stream: Stream<A>, var lazyValue: Lazy<A>?) : Cell<A>(null, stream) {
    override fun sampleNoTrans(): A {
        val lazyValue = lazyValue
        if (value == null && lazyValue != null) {
            value = lazyValue.get()
            this.lazyValue = null
        }
        return value
    }

    override fun setupValue() {
        super.setupValue()
        lazyValue = null
    }
}

