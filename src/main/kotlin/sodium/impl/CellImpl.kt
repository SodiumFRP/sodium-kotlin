package sodium.impl

import sodium.*
import sodium.Stream
import java.util.concurrent.Executor

public open class CellImpl<A>(var value: Event<A>?, val stream: StreamImpl<A>) : Cell<A>, Operational<A> {
    private val listener: Listener
    private var valueUpdate: Event<A>? = null

    init {
        listener = Transaction.apply {
            stream.listen(it, Node.NULL) { trans, newValue ->
                if (valueUpdate == null) {
                    trans.last {
                        setupValue()
                    }
                }
                valueUpdate = newValue
            }
        }

        debugCollector?.visitPrimitive(listener)
    }

    protected open fun setupValue() {
        val newValue = valueUpdate
        if (newValue != null) {
            value = newValue
            valueUpdate = null
        }
    }

    override fun sample(): Event<A> {
        return Transaction.apply {
            sampleNoTrans()
        }
    }

    override fun sampleLazy(): () -> Event<A> {
        return Transaction.apply {
            sampleLazy(it)
        }
    }

    fun sampleLazy(trans: Transaction): () -> Event<A> {
        val s = LazySample(this)
        trans.last {
            s.value = valueUpdate ?: sampleNoTrans()
            s.cell = null
        }
        return {
            s.value ?: s.cell!!.sample()
        }
    }

    open fun sampleNoTrans(): Event<A> {
        return value ?: throw IllegalStateException("Cell has no value!")
    }

    fun value(trans1: Transaction): Stream<A> {
        val out = StreamWithSend<A>()
        trans1.prioritized(out.node) {
            out.send(it, sampleNoTrans())
        }
        val l = stream.listen(trans1, out.node) { trans2, event ->
            out.send(trans2, event)
        }
        debugCollector?.visitPrimitive(l)
        return out.addCleanup(l)
    }

    override fun operational(): Operational<A> {
        return this
    }

    override fun updates(): Stream<A> {
        return stream
    }

    override fun value(): Stream<A> {
        return Transaction.apply {
            value(it)
        }
    }

    override fun changes(): Stream<A> {
        val out = StreamWithSend<A>()
        val l = Transaction.apply {
            stream.listen(it, out.node, ChangesHandler(out, this))
        }
        debugCollector?.visitPrimitive(l)
        return out.addCleanup(l)
    }

    override fun <B> map(transform: (Event<A>) -> B): Cell<B> {
        return Transaction.apply {
            val initial = Lazy.lift(transform, sampleLazy(it))
            val mappedStream = StreamWithSend<B>()
            val l = stream.listen(it, mappedStream.node, CellMapHandler(mappedStream, transform))
            debugCollector?.visitPrimitive(l)
            mappedStream.addCleanup(l)
            LazyCell(mappedStream, initial)
        }
    }

    protected fun finalize() {
        listener.unlisten()
    }

    override fun listen(action: (Event<A>) -> Unit): Listener {
        return Transaction.apply {
            value(it).listen(action)
        }
    }

    override fun listen(executor: Executor, action: (Event<A>) -> Unit): Listener {
        return Transaction.apply {
            value(it).listen(executor, action)
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
//                out.addCleanup(l1).addCleanup(l2).addCleanup(object : Listener() {
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
//                out.addCleanup(listener).holdLazy(za)
//            }
//        }
//    }

    private class LazySample<A>(var cell: CellImpl<A>?) {
        var value: Event<A>? = null
    }
}
