package sodium

import java.lang.ref.WeakReference
import java.util.HashSet

public class Node<A>(private var rank: Long) : Comparable<Node<A>> {
    val listeners = HashSet<Target<A>>()

    /**
     * @return true if any changes were made.
     */
    fun linkTo(action: ((Transaction, A) -> Unit)?, node: Node<*>): Pair<Boolean, Target<A>> {
        val changed = node.ensureBiggerThan(rank)
        val target = Target<A>(action, node)
        listeners.add(target)
        return changed to target
    }

    fun unlinkTo(target: Target<out A>) {
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

    override fun compareTo(other: Node<A>): Int {
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
