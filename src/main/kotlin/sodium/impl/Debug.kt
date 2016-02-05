package sodium.impl

import sodium.Cell
import sodium.Listener
import sodium.Stream
import java.util.HashSet
import java.util.WeakHashMap

private abstract class Direction<A, B>(val from: A, val to: B) {
    abstract fun format(sb: Appendable)

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false

        other as Direction<*, *>

        if (from != other.from) return false
        if (to != other.to) return false

        return true
    }

    override fun hashCode(): Int{
        var result = from?.hashCode() ?: 0
        result += 31 * result + (to?.hashCode() ?: 0)
        return result
    }

}

private fun collectDirections(out: MutableSet<Direction<*, *>>, node: Node<*>) {
    node.listeners.forEach {
        out.add(object : Direction<Node<*>, Node.Target<*>>(node, it) {
            override fun format(sb: Appendable) {
                sb.direction(formatNode(from), formatTarget(to))
            }
        })
        out.add(object : Direction<Node.Target<*>, Node<*>>(it, it.node) {
            override fun format(sb: Appendable) {
                sb.direction(formatTarget(from), formatNode(to))
            }
        })
        val action = it.action.get()
        if (action != null) {
            out.add(object : Direction<Node.Target<*>, Any>(it, action) {
                override fun format(sb: Appendable) {
                    sb.append('\n')
                    sb.single(formatTarget(from))
                    sb.append(" -> ")
                    sb.formatAction(to)
                }
            })
        }

        collectDirections(out, it.node)
    }
}

private fun Appendable.declareNodeStyle(vararg nodes: Node<*>) {
    append("\n{node [shape=box;style=filled;color=lightgrey;]")
    val allNodes = HashSet<Node<*>>()
    nodes.forEach {
        getAllNodes(allNodes, it)
    }
    allNodes.forEach {
        single(formatNode(it))
        append(';')
    }
    append('}')
    allNodes.forEach {
        append('\n')
        single(formatNode(it))
        append(""" [label="${labelNode(it)}"];""")
    }
}

private fun getAllNodes(out: HashSet<Node<*>>, node: Node<*>) {
    out.add(node)

    node.listeners.forEach {
        getAllNodes(out, it.node)
    }
}

private fun Appendable.single(from: String) {
    append('"')
    append(from.toString())
    append('"')
}

private fun Appendable.direction(from: String, to: String) {
    append('\n')
    single(from)
    append(" -> ")
    single(to)
}

fun dump(sb: Appendable, vararg nodes: Node<*>): Unit = with(sb) {
    append('\n')
    append("digraph G {")
    declareNodeStyle(*nodes)

    val directions = HashSet<Direction<*, *>>()
    nodes.forEach {
        collectDirections(directions, it)
    }
    directions.forEach {
        it.format(sb)
    }

    append("\n}")
    Unit
}

fun dump(cell: Cell<*>) {
    dump(System.out, (cell as CellImpl<*>).stream.node)
}

fun dump(vararg stream: Stream<*>) {
    dump(System.out, *(stream.map { (it as StreamImpl<*>).node }.toTypedArray()))
}

private fun labelNode(node: Node<*>): String {
    return formatNode(node) + """\nrank=${node.rank}"""
}

private fun formatNode(node: Node<*>) = "Node:" +  Integer.toString(System.identityHashCode(node), 16).toUpperCase()
private fun formatTarget(node: Node.Target<*>) = "Target:" + Integer.toString(System.identityHashCode(node), 16).toUpperCase()

private fun Appendable.formatAction(action: Any) {
    val info = debugCollector?.info?.get(action)
    val baseName = "action:" + Integer.toString(System.identityHashCode(action), 16).toUpperCase()
    if (info == null) {
        single(baseName)
    } else {
        append("""{"$baseName" [label="${info.opName} - ${info.fileAndLine}"]}""")
    }
}

private fun fileAndLine(element: StackTraceElement): String {
    val fileName = element.fileName
    val line = element.lineNumber
    return "$fileName:$line"
}

class DebugInfo(val opName: String,
                       val fileAndLine: String)

class DebugCollector {
    val info = WeakHashMap<Any, DebugInfo>()

    fun visitPrimitive(listener: Listener) {
        val trace = Thread.currentThread().stackTrace
        val e2 = trace.get(2)
        val opName = e2.methodName
        val e3 = trace.get(3)
        val e = if (e3.className == e2.className && e3.methodName == e2.methodName) {
            trace.get(4)
        } else {
            e3
        }
        val fileAndLine = fileAndLine(e)

        info.put((listener as ListenerImplementation<*>).action, DebugInfo(opName, fileAndLine))
    }
}

var debugCollector: DebugCollector? = null
