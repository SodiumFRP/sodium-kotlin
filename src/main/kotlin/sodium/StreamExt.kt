package sodium

import sodium.impl.*

/**
 * If there's more than one firing in a single transaction, combine them into
 * one using the specified combining function.
 *
 * If the event firings are ordered, then the first will appear at the left
 * input of the combining function. In most common cases it's best not to
 * make any assumptions about the ordering, and the combining function would
 * ideally be commutative.
 */
public fun <A> Stream<A>.coalesce(transform: (Event<A>, Event<A>) -> A): Stream<A> {
    val thiz = this as StreamImpl<A>
    return Transaction.apply2 {
        thiz.coalesce(it, transform)
    }
}

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
    val ea = this as StreamImpl<A>
    val eb = other as StreamImpl<A>
    val out = StreamWithSend<A>()
    val left = Node<A>(0)
    val right = out.node
    // Весь этот блок конструируется отдельно от остальных,
    // поэтому тут нет блокировок и ensureBiggerThan
    right.rank = 1
    val node_target = left.link(right, null)
    val handler = { trans: Transaction, value: Event<A> ->
        out.send(trans, value)
    }
    Transaction.apply2 {
        val l1 = ea.listen(it, left, handler)
        val l2 = eb.listen(it, right, handler)
        out.unsafeAddCleanup(l1).unsafeAddCleanup(l2)
    }

    return out.unsafeAddCleanup(object : Listener {
        override fun unlisten() {
            left.unlink(node_target)
        }
    })
}

/**
 * Merge two streams of events of the same type, combining simultaneous
 * event occurrences.
 *
 * In the case where multiple event occurrences are simultaneous (i.e. all
 * within the same transaction), they are combined using the same logic as
 * 'coalesce'.
 */
public fun <A> Stream<A>.merge(stream: Stream<A>, combine: (Event<A>, Event<A>) -> A): Stream<A> {
    return merge(stream).coalesce(combine)
}

/**
 * Create a behavior with the specified initial value, that gets updated
 * by the values coming through the event. The 'current value' of the behavior
 * is notionally the value as it was 'at the start of the transaction'.
 * That is, state updates caused by event firings get processed at the end of
 * the transaction.
 */
public fun <A> Stream<A>.hold(initValue: A): Cell<A> {
    val thiz = this as StreamImpl<A>
    return Transaction.apply2 {
        CellImpl(Value(initValue), thiz.lastFiringOnly(it))
    }
}

public fun <A> Stream<A>.holdLazy(initValue: () -> A): Cell<A> {
    val thiz = this as StreamImpl<A>
    return Transaction.apply2 {
        LazyCell(thiz.lastFiringOnly(it)) {
            try {
                Value(initValue())
            } catch (e: Exception) {
                Error(e)
            }
        }
    }
}

/**
 * Push each event occurrence in the list onto a new transaction.
 *
 * Does not send events if Error.
 */
public fun <A, C : Collection<A>> Stream<C>.split(): Stream<A> {
    val out = StreamWithSend<A>()
    val thiz = this as StreamImpl<C>
    val listener = Transaction.apply2 {
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
    return out.unsafeAddCleanup(listener)
}
