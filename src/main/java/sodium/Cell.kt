package sodium

public open class Cell<A>(var value: A, protected val stream: Stream<A> = Stream<A>()) {
    var valueUpdate: A = null
    private var listener: Listener? = null
    protected var lazyInitValue: Lazy<A>? = null  // Used by LazyCell

    init {
        Transaction.apply2 {
            listener = stream.listen(Node.NULL, it, false) { trans2, newValue ->
                if (valueUpdate == null) {
                    trans2.last {
                        value = valueUpdate
                        lazyInitValue = null
                        valueUpdate = null
                    }
                }
                valueUpdate = newValue
            }
        }
    }

    /**
     * @return The value including any updates that have happened in this transaction.
     */
    fun newValue(): A {
        return valueUpdate ?: sampleNoTrans()
    }

    /**
     * Sample the cell's current value.

     * This should generally be avoided in favour of value().listen(..) so you don't
     * miss any updates, but in many circumstances it makes sense.

     * It can be best to use it inside an explicit transaction (using Transaction.run()).
     * For example, a b.sample() inside an explicit transaction along with a
     * b.updates().listen(..) will capture the current value and any updates without risk
     * of missing any in between.
     */
    public fun sample(): A {
        return Transaction.apply2 {
            sampleNoTrans()
        }
    }

    /**
     * A variant of sample() that works for CellLoops when they haven't been looped yet.
     */
    public fun sampleLazy(): Lazy<A> {
        return Transaction.apply2 {
            sampleLazy(it)
        }
    }

    fun sampleLazy(trans: Transaction): Lazy<A> {
        val s = LazySample(this)
        trans.last {
            s.value = valueUpdate ?: sampleNoTrans()
            s.hasValue = true
            s.cell = null
        }
        return Lazy {
            if (s.hasValue)
                s.value
            else
                s.cell!!.sample()
        }
    }

    open fun sampleNoTrans(): A {
        return value
    }

    fun updates(trans: Transaction): Stream<A> {
        return stream.lastFiringOnly(trans)
    }

    fun value(trans1: Transaction): Stream<A> {
        val sSpark = StreamSink<Unit>()
        trans1.prioritized(sSpark.node) {
            sSpark.send(it, Unit)
        }
        val sInitial = sSpark.snapshot(this)
        return sInitial.merge(updates(trans1))
    }

    /**
     * Transform the cell's value according to the supplied function.
     */
    public fun <B> map(f: Function1<A, B>): Cell<B> {
        return Transaction.apply2 {
            updates(it).map(f).holdLazy(it, sampleLazy(it).map(f))
        }
    }

    /**
     * Transform a cell with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     */
    public fun <B, S> collect(initState: S, f: Function2<A, S, Pair<B, S>>): Cell<B> {
        return collect(Lazy(initState), f)
    }

    /**
     * Transform a cell with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     * Variant that takes a lazy initial state.
     */
    public fun <B, S> collect(initState: Lazy<S>, f: Function2<A, S, Pair<B, S>>): Cell<B> {
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

    /**
     * Listen for firings of this stream. The returned Listener has an unlisten()
     * method to cause the listener to be removed. This is the observer pattern.
     */
    public fun listen(action: (A) -> Unit): Listener {
        return Transaction.apply {
            value(it).listen(action)
        }
    }

    companion object {

        /**
         * Lift a binary function into cells.
         */
        public fun <A, B, C> lift(f: (A, B) -> C, a: Cell<A>, b: Cell<B>): Cell<C> {
            val bf = a.map(
                    { aa: A ->
                        { bb: B ->
                            f.invoke(aa, bb)
                        }
                    })
            return apply(bf, b)
        }

        /**
         * Lift a ternary function into cells.
         */
        public fun <A, B, C, D> lift(f: Function3<A, B, C, D>, a: Cell<A>, b: Cell<B>, c: Cell<C>): Cell<D> {
            val ffa = object : Function1<A, Function1<B, Function1<C, D>>> {
                override fun invoke(aa: A): Function1<B, Function1<C, D>> {
                    return object : Function1<B, Function1<C, D>> {
                        override fun invoke(bb: B): Function1<C, D> {
                            return object : Function1<C, D> {
                                override fun invoke(cc: C): D {
                                    return f.invoke(aa, bb, cc)
                                }
                            }
                        }
                    }
                }
            }
            val bf = a.map(ffa)
            return apply(apply(bf, b), c)
        }

        /**
         * Lift a quaternary function into cells.
         */
        public fun <A, B, C, D, E> lift(f: Function4<A, B, C, D, E>, a: Cell<A>, b: Cell<B>, c: Cell<C>, d: Cell<D>): Cell<E> {
            val ffa = object : Function1<A, Function1<B, Function1<C, Function1<D, E>>>> {
                override fun invoke(aa: A): Function1<B, Function1<C, Function1<D, E>>> {
                    return object : Function1<B, Function1<C, Function1<D, E>>> {
                        override fun invoke(bb: B): Function1<C, Function1<D, E>> {
                            return object : Function1<C, Function1<D, E>> {
                                override fun invoke(cc: C): Function1<D, E> {
                                    return object : Function1<D, E> {
                                        override fun invoke(dd: D): E {
                                            return f.invoke(aa, bb, cc, dd)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val bf = a.map(ffa)
            return apply(apply(apply(bf, b), c), d)
        }

        /**
         * Lift a 5-argument function into cells.
         */
        public fun <A, B, C, D, E, F> lift(fn: Function5<A, B, C, D, E, F>, a: Cell<A>, b: Cell<B>, c: Cell<C>, d: Cell<D>, e: Cell<E>): Cell<F> {
            val ffa = object : Function1<A, Function1<B, Function1<C, Function1<D, Function1<E, F>>>>> {
                override fun invoke(aa: A): Function1<B, Function1<C, Function1<D, Function1<E, F>>>> {
                    return object : Function1<B, Function1<C, Function1<D, Function1<E, F>>>> {
                        override fun invoke(bb: B): Function1<C, Function1<D, Function1<E, F>>> {
                            return object : Function1<C, Function1<D, Function1<E, F>>> {
                                override fun invoke(cc: C): Function1<D, Function1<E, F>> {
                                    return object : Function1<D, Function1<E, F>> {
                                        override fun invoke(dd: D): Function1<E, F> {
                                            return object : Function1<E, F> {
                                                override fun invoke(ee: E): F {
                                                    return fn.invoke(aa, bb, cc, dd, ee)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val bf = a.map(ffa)
            return apply(apply(apply(apply(bf, b), c), d), e)
        }

        /**
         * Lift a 6-argument function into cells.
         */
        public fun <A, B, C, D, E, F, G> lift(fn: Function6<A, B, C, D, E, F, G>, a: Cell<A>, b: Cell<B>, c: Cell<C>, d: Cell<D>, e: Cell<E>, f: Cell<F>): Cell<G> {
            val ffa = object : Function1<A, Function1<B, Function1<C, Function1<D, Function1<E, Function1<F, G>>>>>> {
                override fun invoke(aa: A): Function1<B, Function1<C, Function1<D, Function1<E, Function1<F, G>>>>> {
                    return object : Function1<B, Function1<C, Function1<D, Function1<E, Function1<F, G>>>>> {
                        override fun invoke(bb: B): Function1<C, Function1<D, Function1<E, Function1<F, G>>>> {
                            return object : Function1<C, Function1<D, Function1<E, Function1<F, G>>>> {
                                override fun invoke(cc: C): Function1<D, Function1<E, Function1<F, G>>> {
                                    return object : Function1<D, Function1<E, Function1<F, G>>> {
                                        override fun invoke(dd: D): Function1<E, Function1<F, G>> {
                                            return object : Function1<E, Function1<F, G>> {
                                                override fun invoke(ee: E): Function1<F, G> {
                                                    return object : Function1<F, G> {
                                                        override fun invoke(ff: F): G {
                                                            return fn.invoke(aa, bb, cc, dd, ee, ff)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val bf = a.map(ffa)
            return apply(apply(apply(apply(apply(bf, b), c), d), e), f)
        }

        /**
         * Apply a value inside a cell to a function inside a cell. This is the
         * primitive for all function lifting.
         */
        public fun <A, B> apply(bf: Cell<Function1<A, B>>, ba: Cell<A>): Cell<B> {
            return Transaction.apply(object : Function1<Transaction, Cell<B>> {
                override fun invoke(trans0: Transaction): Cell<B> {
                    val out = StreamSink<B>()

                    class ApplyHandler() : (Transaction) -> Unit {
                        var f: ((A) -> B)? = null
                        var a: A = null

                        override fun invoke(trans1: Transaction) {
                            trans1.prioritized(out.node) {
                                out.send(it, f!!.invoke(a))
                            }
                        }
                    }

                    val out_target = out.node
                    val in_target = Node(0)
                    val (changed, node_target) = in_target.linkTo(null, out_target)
                    val h = ApplyHandler()
                    val l1 = bf.value(trans0).listen_(in_target, object : TransactionHandler<Function1<A, B>> {
                        override fun run(trans1: Transaction, f: Function1<A, B>) {
                            h.f = f
                            if (h.a != null)
                                h(trans1)
                        }
                    })
                    val l2 = ba.value(trans0).listen_(in_target, object : TransactionHandler<A> {
                        override fun run(trans1: Transaction, a: A) {
                            h.a = a
                            if (h.f != null)
                                h(trans1)
                        }
                    })
                    return out.unsafeAddCleanup(l1).unsafeAddCleanup(l2).unsafeAddCleanup(object : Listener() {
                        override fun unlisten() {
                            in_target.unlinkTo(node_target)
                        }
                    }).holdLazy(Lazy {
                        bf.sampleNoTrans().invoke(ba.sampleNoTrans())
                    })
                }
            })
        }

        /**
         * Unwrap a cell inside another cell to give a time-varying cell implementation.
         */
        public fun <A> switchC(bba: Cell<Cell<A>>): Cell<A> {
            return Transaction.apply(object : Function1<Transaction, Cell<A>> {
                override fun invoke(trans0: Transaction): Cell<A> {
                    val za = bba.sampleLazy().map(object : Function1<Cell<A>, A> {
                        override fun invoke(ba: Cell<A>): A {
                            return ba.sample()
                        }
                    })
                    val out = StreamSink<A>()
                    val h = object : TransactionHandler<Cell<A>> {
                        private var currentListener: Listener? = null

                        override fun invoke(trans2: Transaction, ba: Cell<A>) {
                            // Note: If any switch takes place during a transaction, then the
                            // value().listen will always cause a sample to be fetched from the
                            // one we just switched to. The caller will be fetching our output
                            // using value().listen, and value() throws away all firings except
                            // for the last one. Therefore, anything from the old input behaviour
                            // that might have happened during this transaction will be suppressed.
                            if (currentListener != null)
                                currentListener!!.unlisten()
                            currentListener = ba.value(trans2).listen(out.node, trans2, false, object : TransactionHandler<A> {
                                override fun run(trans3: Transaction, a: A) {
                                    out.send(trans3, a)
                                }
                            })
                        }

                        override fun finalize() {
                            if (currentListener != null)
                                currentListener!!.unlisten()
                        }
                    }
                    val l1 = bba.value(trans0).listen_(out.node, h)
                    return out.unsafeAddCleanup(l1).holdLazy(za)
                }
            })
        }

        /**
         * Unwrap a stream inside a cell to give a time-varying stream implementation.
         */
        public fun <A> switchS(bea: Cell<Stream<A>>): Stream<A> {
            return Transaction.apply(object : Function1<Transaction, Stream<A>> {
                override fun invoke(trans: Transaction): Stream<A> {
                    return switchS(trans, bea)
                }
            })
        }

        private fun <A> switchS(trans1: Transaction, bea: Cell<Stream<A>>): Stream<A> {
            val out = StreamSink<A>()
            val h2 = object : TransactionHandler<A> {
                override fun run(trans2: Transaction, a: A) {
                    out.send(trans2, a)
                }
            }
            val h1 = object : TransactionHandler<Stream<A>> {
                private var currentListener: Listener? = bea.sampleNoTrans().listen(out.node, trans1, false, h2)

                override fun run(trans2: Transaction, ea: Stream<A>) {
                    trans2.last {
                        if (currentListener != null)
                            currentListener!!.unlisten()
                        currentListener = ea.listen(out.node, trans2, true, h2)
                    }
                }

                override fun finalize() {
                    if (currentListener != null)
                        currentListener!!.unlisten()
                }
            }
            val l1 = bea.updates(trans1).listen(out.node, trans1, false, h1)
            return out.unsafeAddCleanup(l1)
        }
    }

    private class LazySample<A>(var cell: Cell<A>?) {
        var hasValue: Boolean = false
        var value: A = null
    }
}
