package sodium.impl

import sodium.*
import sodium.Stream

public open class CellImpl<A>(protected var value: A, protected val stream: StreamImpl<A>) : Cell<A> {
    private var valueUpdate: A = null
    private var listener: Listener? = null

    init {
        Transaction.apply2 {
            listener = stream.listen(Node.NULL, it, false) { trans, newValue ->
                if (valueUpdate == null) {
                    trans.last {
                        setupValue()
                    }
                }
                valueUpdate = newValue
            }
        }
    }

    protected open fun setupValue() {
        value = valueUpdate
        valueUpdate = null
    }

    /**
     * @return The value including any updates that have happened in this transaction.
     */
    fun newValue(): A {
        return valueUpdate ?: sampleNoTrans()
    }

    override fun sample(): A {
        return Transaction.apply2 {
            sampleNoTrans()
        }
    }

    override fun sampleLazy(): () -> A {
        return Transaction.apply2 {
            sampleLazy(it)
        }
    }

    fun sampleLazy(trans: Transaction): () -> A {
        val s = LazySample(this)
        trans.last {
            s.value = valueUpdate ?: sampleNoTrans()
            s.hasValue = true
            s.cell = null
        }
        return {
            if (s.hasValue)
                s.value
            else
                s.cell!!.sample()
        }
    }

    open fun sampleNoTrans(): A {
        return value
    }

    fun updates(trans: Transaction): StreamImpl<A> {
        return stream.lastFiringOnly(trans)
    }

    fun value(trans1: Transaction): Stream<A> {
        val sSpark = StreamWithSend<Unit>()
        trans1.prioritized(sSpark.node) {
            sSpark.send(it, Unit)
        }
        val sInitial = sSpark.snapshot(this)
        return sInitial.merge(updates(trans1))
    }

    override fun <B> map(transform: (A) -> B): Cell<B> {
        return Transaction.apply2 {
            updates(it).map(transform).holdLazy(it, Lazy.lift(transform, sampleLazy(it)))
        }
    }

    override fun <B, S> collect(initState: S, f: (A, S) -> Pair<B, S>): Cell<B> {
        return collect({ initState }, f)
    }

    override fun <B, S> collect(initState: () -> S, f: (A, S) -> Pair<B, S>): Cell<B> {
        return Transaction.apply2 {
            val ea = updates(it).coalesce { fst, snd ->
                snd
            }
            val zbs = Lazy.lift(f, sampleLazy(), initState)
            val ebs = StreamLoop<Pair<B, S>>()
            val bbs = ebs.holdLazy(zbs)
            val bs = bbs.map {
                it.second
            }
            val ebs_out = ea.snapshot(bs, f)
            ebs.loop(ebs_out)
            bbs.map {
                it.first
            }
        }
    }

    protected fun finalize() {
        listener?.unlisten()
    }

    override fun listen(action: (A) -> Unit): Listener {
        return Transaction.apply2 {
            value(it).listen(action)
        }
    }

//    companion object {
//
//        /**
//         * Lift a binary function into cells.
//         */
//        public fun <A, B, C> lift(f: (A, B) -> C, a: Cell<A>, b: Cell<B>): Cell<C> {
//            val bf = a.map(
//                    { aa: A ->
//                        { bb: B ->
//                            f.invoke(aa, bb)
//                        }
//                    })
//            return apply(bf, b)
//        }
//
//        /**
//         * Lift a ternary function into cells.
//         */
//        public fun <A, B, C, D> lift(f: Function3<A, B, C, D>, a: Cell<A>, b: Cell<B>, c: Cell<C>): Cell<D> {
//            val ffa = object : Function1<A, Function1<B, Function1<C, D>>> {
//                override fun invoke(aa: A): Function1<B, Function1<C, D>> {
//                    return object : Function1<B, Function1<C, D>> {
//                        override fun invoke(bb: B): Function1<C, D> {
//                            return object : Function1<C, D> {
//                                override fun invoke(cc: C): D {
//                                    return f.invoke(aa, bb, cc)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            val bf = a.map(ffa)
//            return apply(apply(bf, b), c)
//        }
//
//        /**
//         * Lift a quaternary function into cells.
//         */
//        public fun <A, B, C, D, E> lift(f: Function4<A, B, C, D, E>, a: Cell<A>, b: Cell<B>, c: Cell<C>, d: Cell<D>): Cell<E> {
//            val ffa = object : Function1<A, Function1<B, Function1<C, Function1<D, E>>>> {
//                override fun invoke(aa: A): Function1<B, Function1<C, Function1<D, E>>> {
//                    return object : Function1<B, Function1<C, Function1<D, E>>> {
//                        override fun invoke(bb: B): Function1<C, Function1<D, E>> {
//                            return object : Function1<C, Function1<D, E>> {
//                                override fun invoke(cc: C): Function1<D, E> {
//                                    return object : Function1<D, E> {
//                                        override fun invoke(dd: D): E {
//                                            return f.invoke(aa, bb, cc, dd)
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            val bf = a.map(ffa)
//            return apply(apply(apply(bf, b), c), d)
//        }
//
//        /**
//         * Lift a 5-argument function into cells.
//         */
//        public fun <A, B, C, D, E, F> lift(fn: Function5<A, B, C, D, E, F>, a: Cell<A>, b: Cell<B>, c: Cell<C>, d: Cell<D>, e: Cell<E>): Cell<F> {
//            val ffa = object : Function1<A, Function1<B, Function1<C, Function1<D, Function1<E, F>>>>> {
//                override fun invoke(aa: A): Function1<B, Function1<C, Function1<D, Function1<E, F>>>> {
//                    return object : Function1<B, Function1<C, Function1<D, Function1<E, F>>>> {
//                        override fun invoke(bb: B): Function1<C, Function1<D, Function1<E, F>>> {
//                            return object : Function1<C, Function1<D, Function1<E, F>>> {
//                                override fun invoke(cc: C): Function1<D, Function1<E, F>> {
//                                    return object : Function1<D, Function1<E, F>> {
//                                        override fun invoke(dd: D): Function1<E, F> {
//                                            return object : Function1<E, F> {
//                                                override fun invoke(ee: E): F {
//                                                    return fn.invoke(aa, bb, cc, dd, ee)
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            val bf = a.map(ffa)
//            return apply(apply(apply(apply(bf, b), c), d), e)
//        }
//
//        /**
//         * Lift a 6-argument function into cells.
//         */
//        public fun <A, B, C, D, E, F, G> lift(fn: Function6<A, B, C, D, E, F, G>, a: Cell<A>, b: Cell<B>, c: Cell<C>, d: Cell<D>, e: Cell<E>, f: Cell<F>): Cell<G> {
//            val ffa = object : Function1<A, Function1<B, Function1<C, Function1<D, Function1<E, Function1<F, G>>>>>> {
//                override fun invoke(aa: A): Function1<B, Function1<C, Function1<D, Function1<E, Function1<F, G>>>>> {
//                    return object : Function1<B, Function1<C, Function1<D, Function1<E, Function1<F, G>>>>> {
//                        override fun invoke(bb: B): Function1<C, Function1<D, Function1<E, Function1<F, G>>>> {
//                            return object : Function1<C, Function1<D, Function1<E, Function1<F, G>>>> {
//                                override fun invoke(cc: C): Function1<D, Function1<E, Function1<F, G>>> {
//                                    return object : Function1<D, Function1<E, Function1<F, G>>> {
//                                        override fun invoke(dd: D): Function1<E, Function1<F, G>> {
//                                            return object : Function1<E, Function1<F, G>> {
//                                                override fun invoke(ee: E): Function1<F, G> {
//                                                    return object : Function1<F, G> {
//                                                        override fun invoke(ff: F): G {
//                                                            return fn.invoke(aa, bb, cc, dd, ee, ff)
//                                                        }
//                                                    }
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            val bf = a.map(ffa)
//            return apply(apply(apply(apply(apply(bf, b), c), d), e), f)
//        }
//
//        /**
//         * Apply a value inside a cell to a function inside a cell. This is the
//         * primitive for all function lifting.
//         */
//        public fun <A, B> apply(bf: Cell<(A) -> B>, ba: Cell<A>): Cell<B> {
//            return Transaction.apply2 {
//                val out = StreamSink<B>()
//
//                // TODO: refactor it.
//                class ApplyHandler() : (Transaction) -> Unit {
//                    var action: ((A) -> B)? = null
//                    var value: A = null
//
//                    override fun invoke(trans1: Transaction) {
//                        trans1.prioritized(out.node) {
//                            out.send(it, action!!.invoke(value))
//                        }
//                    }
//                }
//
//                val out_target = out.node
//                val in_target = Node<Any>(0)
//                val (changed, node_target) = in_target.linkTo(null, out_target)
//                val applyHandler = ApplyHandler()
//                val l1 = bf.value(it).listen_(in_target) { trans1, action ->
//                    applyHandler.action = action
//                    if (applyHandler.value != null) {
//                        applyHandler(trans1)
//                    }
//                }
//                val l2 = ba.value(it).listen_(in_target) { trans1, action ->
//                    applyHandler.value = action
//                    if (applyHandler.action != null)
//                        applyHandler(trans1)
//                }
//
//                out.unsafeAddCleanup(l1).unsafeAddCleanup(l2).unsafeAddCleanup(object : Listener() {
//                    override fun unlisten() {
//                        in_target.unlinkTo(node_target)
//                    }
//                }).holdLazy(Lazy {
//                    bf.sampleNoTrans().invoke(ba.sampleNoTrans())
//                })
//            }
//        }
//
//        /**
//         * Unwrap a cell inside another cell to give a time-varying cell implementation.
//         */
//        public fun <A> switchC(bba: Cell<Cell<A>>): Cell<A> {
//            return Transaction.apply2 {
//                val za = bba.sampleLazy().map {
//                    it.sample()
//                }
//                val out = StreamSink<A>()
//                val listener = bba.value(it).listen_(out.node) { trans2, ba ->
//                    // Note: If any switch takes place during a transaction, then the
//                    // value().listen will always cause a sample to be fetched from the
//                    // one we just switched to. The caller will be fetching our output
//                    // using value().listen, and value() throws away all firings except
//                    // for the last one. Therefore, anything from the old input behaviour
//                    // that might have happened during this transaction will be suppressed.
//                    // TODO: do something with memory leak here (if any).
//                    ba.value(trans2).listen(out.node, trans2, false) { trans3, a ->
//                        out.send(trans3, a)
//                    }
//                }
//                out.unsafeAddCleanup(listener).holdLazy(za)
//            }
//        }
//
//        /**
//         * Unwrap a stream inside a cell to give a time-varying stream implementation.
//         */
//        public fun <A> switchS(bea: Cell<Stream<A>>): Stream<A> {
//            return Transaction.apply2 {
//                val trans1 = it
//                val out = StreamSink<A>()
//                val h2 = { trans2: Transaction, a: A ->
//                    out.send(trans2, a)
//                }
//                val currentListener: Listener = bea.sampleNoTrans().listen(out.node, trans1, false, h2)
//                val l1 = bea.updates(trans1).listen(out.node, trans1, false) { trans2, ea ->
//                    trans2.last {
//                        currentListener.unlisten()
//                        // TODO: do something with memory leak here (if any).
//                        ea.listen(out.node, trans2, true, h2)
//                    }
//                }
//                out.unsafeAddCleanup(l1)
//            }
//        }
//    }

    private class LazySample<A>(var cell: Cell<A>?) {
        var hasValue: Boolean = false
        var value: A = null
    }
}
