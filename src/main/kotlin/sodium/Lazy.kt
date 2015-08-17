package sodium

public inline fun <A, B> (() -> Event<A>).map(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (Event<A>) -> B): () -> B {
    return {
        f(this())
    }
}

public object Lazy {
    /**
     * Like map from Lazy<A> to Lazy<B>
     */
    public inline fun <A, B> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: Event<A>.() -> B,
                                  inlineOptions(InlineOption.ONLY_LOCAL_RETURN) a: () -> Event<A>): () -> B {
        return {
            a().f()
        }
    }

    /**
     * Lift a binary function into lazy values.
     */
    public inline fun <A, B, C> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (Event<A>, Event<B>) -> C,
                                     inlineOptions(InlineOption.ONLY_LOCAL_RETURN) a: () -> Event<A>,
                                     inlineOptions(InlineOption.ONLY_LOCAL_RETURN) b: () -> Event<B>): () -> C {
        return {
            f(a(), b())
        }
    }

    /**
     * Lift a ternary function into lazy values.
     */
    public inline fun <A, B, C, D> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (A, B, C) -> D,
                                        inlineOptions(InlineOption.ONLY_LOCAL_RETURN) a: () -> A,
                                        inlineOptions(InlineOption.ONLY_LOCAL_RETURN) b: () -> B,
                                        inlineOptions(InlineOption.ONLY_LOCAL_RETURN) c: () -> C): () -> D {
        return {
            f(a(), b(), c())
        }
    }

    /**
     * Lift a quaternary function into lazy values.
     */
    public inline fun <A, B, C, D, E> lift(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: (A, B, C, D) -> E,
                                           inlineOptions(InlineOption.ONLY_LOCAL_RETURN) a: () -> A,
                                           inlineOptions(InlineOption.ONLY_LOCAL_RETURN) b: () -> B,
                                           inlineOptions(InlineOption.ONLY_LOCAL_RETURN) c: () -> C,
                                           inlineOptions(InlineOption.ONLY_LOCAL_RETURN) d: () -> D): () -> E {
        return {
            f(a(), b(), c(), d())
        }
    }
}
