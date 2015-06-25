package sodium

open class LazyCell<A>(event: Stream<A>, lazyInitValue: Lazy<A>) : Cell<A>(null, event) {
    init {
        this.lazyInitValue = lazyInitValue
    }

    override fun sampleNoTrans(): A {
        if (value == null && lazyInitValue != null) {
            value = lazyInitValue!!.get()
            lazyInitValue = null
        }
        return value
    }
}

