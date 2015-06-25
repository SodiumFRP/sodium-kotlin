package sodium

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashSet

public class Node(private var rank: Long) : Comparable<Node> {

    public class Target(action: TransactionHandler<*>?, val node: Node) {
        val action = WeakReference(action)
    }

    var listeners: MutableList<Target> = ArrayList()

    /**
     * @return true if any changes were made.
     */
    fun linkTo(action: TransactionHandler<*>?, node: Node): Pair<Boolean, Target> {
        val changed = node.ensureBiggerThan(rank, HashSet<Node>())
        val target = Target(action, node)
        listeners.add(target)
        return changed to target
    }

    fun unlinkTo(target: Target) {
        listeners.remove(target)
    }

    private fun ensureBiggerThan(limit: Long, visited: MutableSet<Node>): Boolean {
        if (rank > limit || visited.contains(this))
            return false

        visited.add(this)
        rank = limit + 1
        for (l in listeners)
            l.node.ensureBiggerThan(rank, visited)
        return true
    }

    override fun compareTo(other: Node): Int {
        if (rank < other.rank) return -1
        if (rank > other.rank) return 1
        return 0
    }

    companion object {
        public val NULL: Node = Node(Long.MAX_VALUE)
    }
}
