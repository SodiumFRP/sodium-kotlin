package sodium

import java.util.ArrayList

public open class Stream<A>(
        val node: Node,
        val finalizers: MutableList<Listener>,
        val firings: MutableList<A>) {

    /**
     * An event that never fires.
     */
    public constructor() : this(Node(0L), ArrayList<Listener>(), ArrayList<A>()) {
    }


    /**
     * Listen for firings of this event. The returned Listener has an unlisten()
     * method to cause the listener to be removed. This is the observer pattern.
     */
    public fun listen(action: (A) -> Unit): Listener {
        return listen_(Node.NULL, object : TransactionHandler<A> {
            override fun invoke(trans2: Transaction, a: A) {
                action(a)
            }
        })
    }

    fun listen_(target: Node, action: TransactionHandler<A>): Listener {
        return Transaction.apply {
            listen(target, it, false, action)
        }
    }

    fun listen(target: Node, trans: Transaction, suppressEarlierFirings: Boolean, action: (Transaction, newValue: A) -> Unit): Listener {
        val nodeTarget = synchronized (Transaction.listenersLock) {
            val (changed, nodeTarget) = node.linkTo(action, target)
            if (changed)
                trans.toRegen = true
            nodeTarget
        }

        val firings = ArrayList(this.firings)
        if (!suppressEarlierFirings && !firings.isEmpty()) {
            trans.prioritized(target) {
                // Anything sent already in this transaction must be sent now so that
                // there's no order dependency between send and listen.
                for (a in firings) {
                    Transaction.inCallback++
                    try {
                        // Don't allow transactions to interfere with Sodium
                        // internals.
                        action(trans, a)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    } finally {
                        Transaction.inCallback--
                    }
                }
            }
        }
        return ListenerImplementation(this, action, nodeTarget)
    }

    /**
     * Transform the event's value according to the supplied function.
     */
    public fun <B> map(transform: Function1<A, B>): Stream<B> {
        val out = StreamSink<B>()
        val l = listen_(out.node, object : TransactionHandler<A> {
            override fun run(trans2: Transaction, a: A) {
                out.send(trans2, transform(a))
            }
        })
        return out.unsafeAddCleanup(l)
    }

    /**
     * Create a behavior with the specified initial value, that gets updated
     * by the values coming through the event. The 'current value' of the behavior
     * is notionally the value as it was 'at the start of the transaction'.
     * That is, state updates caused by event firings get processed at the end of
     * the transaction.
     */
    public fun hold(initValue: A): Cell<A> {
        return Transaction.apply {
            Cell(initValue, lastFiringOnly(it))
        }
    }

    public fun holdLazy(initValue: Lazy<A>): Cell<A> {
        return Transaction.apply {
            holdLazy(it, initValue)
        }
    }

    fun holdLazy(trans: Transaction, initValue: Lazy<A>): Cell<A> {
        return LazyCell(lastFiringOnly(trans), initValue)
    }

    /**
     * Variant of snapshot that throws away the event's value and captures the behavior's.
     */
    public fun <B> snapshot(beh: Cell<B>): Stream<B> {
        return snapshot(beh) { a, b ->
            b
        }
    }

    /**
     * Sample the behavior at the time of the event firing. Note that the 'current value'
     * of the behavior that's sampled is the value as at the start of the transaction
     * before any state changes of the current transaction are applied through 'hold's.
     */
    public fun <B, C> snapshot(b: Cell<B>, f: (A, B) -> C): Stream<C> {
        val out = StreamSink<C>()
        val l = listen_(out.node, object : TransactionHandler<A> {
            override fun run(trans2: Transaction, a: A) {
                out.send(trans2, f(a, b.sampleNoTrans()))
            }
        })
        return out.unsafeAddCleanup(l)
    }

    /**
     * Merge two streams of events of the same type.

     * In the case where two event occurrences are simultaneous (i.e. both
     * within the same transaction), both will be delivered in the same
     * transaction. If the event firings are ordered for some reason, then
     * their ordering is retained. In many common cases the ordering will
     * be undefined.
     */
    public fun merge(stream: Stream<A>): Stream<A> {
        return Stream.merge(this, stream)
    }

    /**
     * Push this event occurrence onto a new transaction. Same as split() but works
     * on a single value.
     */
    public fun defer(): Stream<A> {
        val out = StreamSink<A>()
        val l1 = listen_(out.node, object : TransactionHandler<A> {
            override fun invoke(trans: Transaction, a: A) {
                trans.post {
                    val newTrans = Transaction()
                    try {
                        out.send(newTrans, a)
                    } finally {
                        newTrans.close()
                    }
                }
            }
        })
        return out.unsafeAddCleanup(l1)
    }

    /**
     * If there's more than one firing in a single transaction, combine them into
     * one using the specified combining function.

     * If the event firings are ordered, then the first will appear at the left
     * input of the combining function. In most common cases it's best not to
     * make any assumptions about the ordering, and the combining function would
     * ideally be commutative.
     */
    public fun coalesce(f: (A, A) -> A): Stream<A> {
        return Transaction.apply {
            coalesce(it, f)
        }
    }

    fun coalesce(transaction: Transaction, combine: (A, A) -> A): Stream<A> {
        val out = StreamSink<A>()
        val handler = CoalesceHandler(combine, out)
        val listener = listen(out.node, transaction, false, handler)
        return out.unsafeAddCleanup(listener)
    }

    /**
     * Clean up the output by discarding any firing other than the last one.
     */
    fun lastFiringOnly(trans: Transaction): Stream<A> {
        return coalesce(trans) {first, second ->
            second
        }
    }

    /**
     * Merge two streams of events of the same type, combining simultaneous
     * event occurrences.

     * In the case where multiple event occurrences are simultaneous (i.e. all
     * within the same transaction), they are combined using the same logic as
     * 'coalesce'.
     */
    public fun merge(stream: Stream<A>, combine: Function2<A, A, A>): Stream<A> {
        return merge(stream).coalesce(combine)
    }

    /**
     * Only keep event occurrences for which the predicate returns true.
     */
    public fun filter(f: Function1<A, Boolean>): Stream<A> {
        val out = StreamSink<A>()
        val l = listen_(out.node, object : TransactionHandler<A> {
            override fun run(trans2: Transaction, a: A) {
                if (f.invoke(a)) out.send(trans2, a)
            }
        })
        return out.unsafeAddCleanup(l)
    }

    /**
     * Filter out any event occurrences whose value is a Java null pointer.
     */
    public fun filterNotNull(): Stream<A> {
        return filter {
            it != null
        }
    }

    /**
     * Filter the empty values out, and strip the Optional wrapper from the present ones.
     */
    //    public static <A> Stream<A> filterOptional(final Stream<Optional<A>> ev)
    //    {
    //        final StreamSink<A> out = new StreamSink<A>();
    //        final Listener l = ev.listen_(out.node, new TransactionHandler<Optional<A>>() {
    //        	@Override
    //            public void run(final Transaction trans2, final Optional<A> oa) {
    //	            if (oa.isPresent()) out.send(trans2, oa.get());
    //	        }
    //        });
    //        return out.unsafeAddCleanup(l);
    //    }

    /**
     * Let event occurrences through only when the behavior's value is True.
     * Note that the behavior's value is as it was at the start of the transaction,
     * that is, no state changes from the current transaction are taken into account.
     */
    public fun gate(predicate: Cell<Boolean>): Stream<A> {
        return snapshot(predicate) { event, predicateValue ->
            if (predicateValue) event else null
        }.filterNotNull() as Stream<A>
    }

    /**
     * Transform an event with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     */
    public fun <B, S> collect(initState: S, f: (A, S) -> Pair<B, S>): Stream<B> {
        return collectLazy(Lazy(initState), f)
    }

    /**
     * Transform an event with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     */
    public fun <B, S> collectLazy(initState: Lazy<S>, f: (A, S) -> Pair<B, S>): Stream<B> {
        return Transaction.apply {
            val ea = this@Stream
            val es = StreamLoop<S>()
            val s = es.holdLazy(initState)
            val ebs = ea.snapshot(s, f)
            val eb = ebs.map {
                it.first
            }
            val es_out = ebs.map {
                it.second
            }
            es.loop(es_out)
            eb
        }
    }

    /**
     * Accumulate on input event, outputting the new state each time.
     */
    public fun <S> accum(initState: S, f: (A, S) -> S): Cell<S> {
        return accumLazy(Lazy(initState), f)
    }

    /**
     * Accumulate on input event, outputting the new state each time.
     * Variant that takes a lazy initial state.
     */
    public fun <S> accumLazy(initState: Lazy<S>, f: (A, S) -> S): Cell<S> {
        return Transaction.apply {
            val ea = this@Stream
            val es = StreamLoop<S>()
            val s = es.holdLazy(initState)
            val es_out = ea.snapshot(s, f)
            es.loop(es_out)
            es_out.holdLazy(initState)
        }
    }

    /**
     * Throw away all event occurrences except for the first one.
     */
    public fun once(): Stream<A> {
        // This is a bit long-winded but it's efficient because it deregisters
        // the listener.
        val la = arrayOfNulls<Listener>(1)
        val out = StreamSink<A>()
        la[0] = listen_(out.node, object : TransactionHandler<A> {
            override fun run(trans: Transaction, a: A) {
                val listener = la[0]
                if (listener != null) {
                    out.send(trans, a)
                    listener.unlisten()
                    la[0] = null
                }
            }
        })
        val listener = la[0]
        return if (listener == null) this else out.unsafeAddCleanup(listener)
    }

    fun unsafeAddCleanup(cleanup: Listener): Stream<A> {
        finalizers.add(cleanup)
        return this
    }

    public fun addCleanup(cleanup: Listener): Stream<A> {
        val fsNew = ArrayList(finalizers)
        fsNew.add(cleanup)
        return Stream(node, fsNew, firings)
    }

    protected fun finalize() {
        for (l in finalizers)
            l.unlisten()
    }

    companion object {

        /**
         * Merge two streams of events of the same type.

         * In the case where two event occurrences are simultaneous (i.e. both
         * within the same transaction), both will be delivered in the same
         * transaction. If the event firings are ordered for some reason, then
         * their ordering is retained. In many common cases the ordering will
         * be undefined.
         */
        private fun <A> merge(ea: Stream<A>, eb: Stream<A>): Stream<A> {
            val out = StreamSink<A>()
            val left = Node(0)
            val right = out.node
            val (changed, node_target) = left.linkTo(null, right)
            val h = object : TransactionHandler<A> {
                override fun run(trans: Transaction, a: A) {
                    out.send(trans, a)
                }
            }
            val l1 = ea.listen_(left, h)
            val l2 = eb.listen_(right, h)
            return out.unsafeAddCleanup(l1).unsafeAddCleanup(l2).unsafeAddCleanup(object : Listener() {
                override fun unlisten() {
                    left.unlinkTo(node_target)
                }
            })
        }

        /**
         * Push each event occurrence in the list onto a new transaction.
         */
        public fun <A, C : Collection<A>> split(s: Stream<C>): Stream<A> {
            val out = StreamSink<A>()
            val listener = s.listen_(out.node, object : TransactionHandler<C> {
                override fun invoke(trans: Transaction, events: C) {
                    trans.post {
                        for (event in events) {
                            val newTransaction = Transaction()
                            try {
                                out.send(newTransaction, event)
                            } finally {
                                newTransaction.close()
                            }
                        }
                    }
                }
            })
            return out.unsafeAddCleanup(listener)
        }
    }
}

class ListenerImplementation<A>(
        /**
         * It's essential that we keep the listener alive while the caller holds
         * the Listener, so that the finalizer doesn't get triggered.
         */
        private var event: Stream<A>?,
        /**
         * It's also essential that we keep the action alive, since the node uses
         * a weak reference.
         */
        private var action: TransactionHandler<A>?,
        private var target: Node.Target?) : Listener() {

    override fun unlisten() {
        synchronized (Transaction.listenersLock) {
            val stream = event
            val node = target
            if (stream != null && node != null) {
                stream.node.unlinkTo(node)
                event = null
                action = null
                target = null
            }
        }
    }
}

class CoalesceHandler<A>(private val f: Function2<A, A, A>, private val out: StreamSink<A>) : TransactionHandler<A> {
    private var accumValid: Boolean = false
    private var accum: A = null

    override fun invoke(trans1: Transaction, a: A) {
        if (accumValid) {
            accum = f(accum, a)
        } else {
            trans1.prioritized(out.node) {
                out.send(it, accum)
                accumValid = false
                accum = null
            }
            accum = a
            accumValid = true
        }
    }
}

