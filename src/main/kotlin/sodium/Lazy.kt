package sodium

public class Lazy<A> {
    public constructor(f: () -> A) {
        this.f = f
    }

    public constructor(a: A) {
        f = { a }
    }

    private val f: () -> A

    public fun get(): A = f()

    /**
     * Map the lazy value according to the specified function.
     */
    public fun <B> map(f2: (A) -> B): Lazy<B> {
        return Lazy {
            f2(get())
        }
    }

    companion object {

        /**
         * Lift a binary function into lazy values.
         */
        public inline fun <A, B, C> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (A, B) -> C,
                                         a: Lazy<A>, b: Lazy<B>): Lazy<C> {
            return Lazy {
                f(a.get(), b.get())
            }
        }

        /**
         * Lift a ternary function into lazy values.
         */
        public inline fun <A, B, C, D> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (A, B, C) -> D,
                                            a: Lazy<A>, b: Lazy<B>, c: Lazy<C>): Lazy<D> {
            return Lazy {
                f(a.get(), b.get(), c.get())
            }
        }

        /**
         * Lift a quaternary function into lazy values.
         */
        public inline fun <A, B, C, D, E> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (A, B, C, D) -> E,
                                               a: Lazy<A>, b: Lazy<B>, c: Lazy<C>, d: Lazy<D>): Lazy<E> {
            return Lazy {
                f(a.get(), b.get(), c.get(), d.get())
            }
        }
    }
}

