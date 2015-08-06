package sodium

import sodium.impl.*

/**
 * Merge two streams of events of the same type.
 *
 * In the case where two event occurrences are simultaneous (i.e. both
 * within the same transaction), both will be delivered in the same
 * transaction. If the event firings are ordered for some reason, then
 * their ordering is retained. In many common cases the ordering will
 * be undefined.
 */
public fun <A> Stream<A>.merge(other: Stream<A>): Stream<A> {
    return merge(other) { a: Event<A>, b: Event<A> ->
        b.value
    }
}

/**
 * Merge two streams of events of the same type, combining simultaneous
 * event occurrences.
 *
 * In the case where multiple event occurrences are simultaneous (i.e. all
 * within the same transaction), they are combined using the same logic as
 * 'coalesce'.
 */
public fun <A> Stream<A>.merge(other: Stream<A>, combine: (Event<A>, Event<A>) -> A): Stream<A> {
    val ea = this as StreamImpl<A>
    val eb = other as StreamImpl<A>
    val out = StreamWithSend<A>()
    val left = Node<A>(0)
    val right = Node<A>(1)
    out.node.rank = 2
    left.link(right, null)
    val node_target1 = left.link(out.node, null)
    val node_target2 = right.link(out.node, null)
    val handler = MergeHandler(out, combine)

    Transaction.apply {
        val l1 = ea.listen(it, left, handler)
        val l2 = eb.listen(it, right, handler)
        debugCollector?.visitPrimitive(l1)
        out.addCleanup(l1).addCleanup(l2)
    }

    return out.addCleanup(object : ListenerImpl() {
        override fun unlisten() {
            left.unlink(node_target1)
            right.unlink(node_target2)
        }
    })
}

/**
 * Create a behavior with the specified initial value, that gets updated
 * by the values coming through the event. The 'current value' of the behavior
 * is notionally the value as it was 'at the start of the transaction'.
 * That is, state updates caused by event firings get processed at the end of
 * the transaction.
 */
public fun <A> Stream<A>.hold(initValue: A): Cell<A> {
    return CellImpl(Value(initValue), this as StreamImpl<A>)
}

public fun <A> Stream<A>.holdLazy(initValue: () -> A): Cell<A> {
    return LazyCell(this as StreamImpl<A>, initValue)
}

/**
 * Push each event occurrence in the list onto a new transaction.
 *
 * Does not send events if Error.
 */
public fun <A, C : Collection<A>> Stream<C>.split(): Stream<A> {
    val out = StreamWithSend<A>()
    val thiz = this as StreamImpl<C>
    val listener = Transaction.apply {
        thiz.listen(it, out.node) { trans, events ->
            trans.post {
                val safeEvents = try {
                    events.value
                } catch(e: Exception) {
                    emptyList<A>()
                }

                for (event in safeEvents) {
                    val newTransaction = Transaction()
                    try {
                        out.send(newTransaction, Value(event))
                    } finally {
                        newTransaction.close()
                    }
                }
            }
        }
    }
    return out.addCleanup(listener)
}

/**
 * Unwrap a stream inside a cell to give a time-varying stream implementation.
 */
public fun <A> Cell<Stream<A>?>.switchS(): Stream<A> {
    val out = StreamWithSend<A>()
    val listener = Transaction.apply {
        val bea = this as CellImpl<Stream<A>?>

        val l1 = try {
            (bea.sampleNoTrans().value as? StreamImpl<A>)?.listen(it, out.node, DirectToOutHandler(out))
        } catch (e: Exception) {
            out.send(it, Error<A>(e))
            null
        }

        bea.stream.listen(it, out.node, FlattenHandler(out, l1))
    }
    return out.addCleanup(listener)
}

public fun <A> Stream<Stream<A>?>.flatten(): Stream<A> {
    val out = StreamWithSend<A>()
    val thiz = this as StreamImpl<Stream<A>?>
    val listener = Transaction.apply {
        thiz.listen(it, out.node, FlattenHandler(out))
    }
    return out.addCleanup(listener)
}

/**
 * Filter out any event occurrences whose value is a Java null pointer.
 */
@suppress("UNCHECKED_CAST", "BASE_WITH_NULLABLE_UPPER_BOUND")
public fun <A> Stream<A?>.filterNotNull(): StreamImpl<A> {
    val out = StreamWithSend<A>()
    val thiz = this as StreamImpl<A?>
    val l = Transaction.apply {
        thiz.listen(it, out.node) { trans2, a ->
            try {
                if (a.value != null) {
                    out.send(trans2, a as Event<A>)
                }
            } catch (e: Exception) {
                Sodium.unhandledExceptions?.invoke(e)
            }
        }
    }
    debugCollector?.visitPrimitive(l)
    return out.addCleanup(l)
}
