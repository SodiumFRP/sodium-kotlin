package sodium

import java.lang.ref.WeakReference
import java.util.HashSet

public class Node(private var rank: Long) : Comparable<Node> {

    public class Target(action: TransactionHandler<*>?, val node: Node) {
        val action = WeakReference(action)
    }

    val listeners = HashSet<Target>()

    /**
     * @return true if any changes were made.
     */
    fun linkTo(action: TransactionHandler<*>?, node: Node): Pair<Boolean, Target> {
        val changed = node.ensureBiggerThan(rank)
        val target = Target(action, node)
        listeners.add(target)
        return changed to target
    }

    fun unlinkTo(target: Target) {
        listeners.remove(target)
    }

    private fun ensureBiggerThan(limit: Long): Boolean {
        if (rank > limit)
            return false

        rank = limit + 1
        for (l in listeners)
            l.node.ensureBiggerThan(rank)
        return true
    }

    override fun compareTo(other: Node): Int {
        return when {
            rank < other.rank -> -1
            rank > other.rank -> 1
            else -> 0
        }
    }

    companion object {
        public val NULL: Node = Node(Long.MAX_VALUE)
    }
}
