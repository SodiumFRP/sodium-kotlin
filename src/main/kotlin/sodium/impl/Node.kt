package sodium.impl

import sodium.Event
import java.lang.ref.WeakReference
import java.util.HashSet

public class Node<A>(var rank: Long) : Comparable<Node<*>> {
    val listeners = HashSet<Node<*>>()
    var action: WeakReference<((Transaction, Event<*>) -> Unit)?>? = null

    @suppress("NOTHING_TO_INLINE")
    inline fun link(node: Node<*>, noinline action: ((Transaction, Event<A>) -> Unit)?): Node<*> {
        node.action = WeakReference(action as ((Transaction, Event<*>) -> Unit)?)
        listeners.add(node)
        return node
    }

    @suppress("NOTHING_TO_INLINE")
    inline fun unlink(target: Node<*>) {
        listeners.remove(target)
    }

    /**
     * @return true if any changes were made.
     */
    fun ensureBiggerThan(limit: Long): Boolean {
        if (rank > limit)
            return false

        rank = limit + 1

        for (listener in listeners) {
            listener.ensureBiggerThan(rank)
        }

        return true
    }

    override fun compareTo(other: Node<*>): Int {
        return when {
            rank < other.rank -> -1
            rank > other.rank -> 1
            else -> 0
        }
    }
}
