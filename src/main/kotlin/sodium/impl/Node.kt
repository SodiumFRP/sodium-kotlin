package sodium.impl

import sodium.Event
import java.lang.ref.WeakReference
import java.util.HashSet

public class Node<A>(var rank: Long) : Comparable<Node<*>> {
    val listeners = HashSet<Target<A>>()

    @Suppress("NOTHING_TO_INLINE")
    inline fun link(node: Node<*>, noinline action: ((Transaction, Event<A>) -> Unit)?): Target<A> {
        val target = Target(action, node)
        listeners.add(target)
        return target
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun unlink(target: Target<out A>) {
        listeners.removeRaw(target)
    }

    /**
     * @return true if any changes were made.
     */
    fun ensureBiggerThan(limit: Long): Boolean {
        if (rank > limit)
            return false

        rank = limit + 1

        for (listener in listeners) {
            listener.node.ensureBiggerThan(rank)
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

    companion object {
        public val NULL: Node<Any> = Node(Long.MAX_VALUE)
    }

    public class Target<A>(action: ((Transaction, Event<A>) -> Unit)?, val node: Node<*>) {
        val action = WeakReference(action)
    }
}
