package sodium

public open class Listener {

    public open fun unlisten() {
    }

    /**
     * Combine listeners into one where a single unlisten() invocation will unlisten
     * both the inputs.
     */
    public fun append(two: Listener): Listener {
        val one = this
        return object : Listener() {
            override fun unlisten() {
                one.unlisten()
                two.unlisten()
            }
        }
    }
}

