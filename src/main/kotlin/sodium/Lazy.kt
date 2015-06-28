package sodium

public class Lazy<A> {
    public constructor(f: Function0<A>) {
        this.f = f
    }

    public constructor(a: A) {
        f = object : Function0<A> {
            override fun invoke(): A {
                return a
            }
        }
    }

    private val f: Function0<A>

    public fun get(): A {
        return f.invoke()
    }

    /**
     * Map the lazy value according to the specified function.
     */
    public fun <B> map(f2: Function1<A, B>): Lazy<B> {
        return Lazy {
            f2.invoke(get())
        }
    }

    companion object {

        /**
         * Lift a binary function into lazy values.
         */
        public fun <A, B, C> lift(f: Function2<A, B, C>, a: Lazy<A>, b: Lazy<B>): Lazy<C> {
            return Lazy(object : Function0<C> {
                override fun invoke(): C {
                    return f.invoke(a.get(), b.get())
                }
            })
        }

        /**
         * Lift a ternary function into lazy values.
         */
        public fun <A, B, C, D> lift(f: Function3<A, B, C, D>, a: Lazy<A>, b: Lazy<B>, c: Lazy<C>): Lazy<D> {
            return Lazy(object : Function0<D> {
                override fun invoke(): D {
                    return f.invoke(a.get(), b.get(), c.get())
                }
            })
        }

        /**
         * Lift a quaternary function into lazy values.
         */
        public fun <A, B, C, D, E> lift(f: Function4<A, B, C, D, E>, a: Lazy<A>, b: Lazy<B>, c: Lazy<C>, d: Lazy<D>): Lazy<E> {
            return Lazy(object : Function0<E> {
                override fun invoke(): E {
                    return f.invoke(a.get(), b.get(), c.get(), d.get())
                }
            })
        }
    }
}

