package sodium.impl

import sodium.Transaction
import java.lang.ref.WeakReference
import java.util.HashSet

public class Node<A>(var rank: Long) : Comparable<Node<*>> {
    val listeners = HashSet<Target<A>>()

    /**
     * @return true if any changes were made.
     */
    fun link(node: Node<*>, action: ((Transaction, A) -> Unit)?): Pair<Boolean, Target<A>> {
        val changed = node.ensureBiggerThan(rank)
        val target = Target<A>(action, node)
        listeners.add(target)
        return changed to target
    }

    fun unlink(target: Target<out A>) {
        listeners.remove(target)
    }

    private fun ensureBiggerThan(limit: Long): Boolean {
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

    public class Target<A>(action: ((Transaction, A) -> Unit)?, val node: Node<*>) {
        val action = WeakReference(action)
    }
}
