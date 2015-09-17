package sodium

public object Lazy {
    /**
     * Like map from Lazy<A> to Lazy<B>
     */
    public inline fun <A, B> lift(crossinline f: Event<A>.() -> B,
                                  crossinline a: () -> Event<A>): () -> B {
        return {
            a().f()
        }
    }

    /**
     * Lift a binary function into lazy values.
     */
    public inline fun <A, B, C> lift(crossinline f: (Event<A>, Event<B>) -> C,
                                     crossinline a: () -> Event<A>,
                                     crossinline b: () -> Event<B>): () -> C {
        return {
            f(a(), b())
        }
    }

    /**
     * Lift a ternary function into lazy values.
     */
    public inline fun <A, B, C, D> lift(crossinline f: (A, B, C) -> D,
                                        crossinline a: () -> A,
                                        crossinline b: () -> B,
                                        crossinline c: () -> C): () -> D {
        return {
            f(a(), b(), c())
        }
    }

    /**
     * Lift a quaternary function into lazy values.
     */
    public inline fun <A, B, C, D, E> lift(crossinline f: (A, B, C, D) -> E,
                                           crossinline a: () -> A,
                                           crossinline b: () -> B,
                                           crossinline c: () -> C,
                                           crossinline d: () -> D): () -> E {
        return {
            f(a(), b(), c(), d())
        }
    }
}
