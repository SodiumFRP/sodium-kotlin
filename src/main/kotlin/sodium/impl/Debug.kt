package sodium.impl

import sodium.Cell
import sodium.Listener
import sodium.Stream
import java.util.HashSet
import java.util.WeakHashMap

private abstract data class Direction<A, B>(val from: A, val to: B) {
    public abstract fun format(sb: Appendable)
}

private fun collectDirections(out: MutableSet<Direction<*, *>>, node: Node<*>) {
    node.listeners.forEach {
        out.add(object : Direction<Node<*>, Node<*>>(node, it) {
            override fun format(sb: Appendable) {
                sb.direction(formatNode(from), formatNode(to))
            }
        })
        collectDirections(out, it)
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
        append(""" [label="${labelNode(it)}""")

        val action = it.action?.get()
        if (action != null) {
            append("\\n")
            formatAction(action)
        }
        append(""""];""")
    }
}

private fun getAllNodes(out: HashSet<Node<*>>, node: Node<*>) {
    out.add(node)

    node.listeners.forEach {
        getAllNodes(out, it)
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

public fun dump(sb: Appendable, vararg nodes: Node<*>): Unit = with(sb) {
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

public fun dump(cell: Cell<*>) {
    dump(System.out, (cell as CellImpl<*>).stream.node)
}

public fun dump(vararg stream: Stream<*>) {
    dump(System.out, *(stream.map { (it as StreamImpl<*>).node }.toTypedArray()))
}

private fun labelNode(node: Node<*>): String {
    return formatNode(node) + """\nrank=${node.rank}"""
}

private fun formatNode(node: Node<*>) = "Node:" +  Integer.toString(System.identityHashCode(node), 16).toUpperCase()

private fun Appendable.formatAction(action: Any) {
    val info = debugCollector?.info?.get(action)
    if (info == null) {
        single("action:" + Integer.toString(System.identityHashCode(action), 16).toUpperCase())
    } else {
        append("""${info.opName} - ${info.fileAndLine}""")
    }
}

private fun fileAndLine(element: StackTraceElement): String {
    val fileName = element.getFileName()
    val line = element.getLineNumber()
    return "$fileName:$line"
}

public class DebugInfo(val opName: String,
                       val fileAndLine: String)

public class DebugCollector {
    val info = WeakHashMap<Any, DebugInfo>()

    public fun visitPrimitive(listener: Listener) {
        val trace = Thread.currentThread().getStackTrace()
        val e2 = trace.get(2)
        val opName = e2.getMethodName()
        val e3 = trace.get(3)
        val e = if (e3.getClassName() == e2.getClassName() && e3.getMethodName() == e2.getMethodName()) {
            trace.get(4)
        } else {
            e3
        }
        val fileAndLine = fileAndLine(e)

        info.put((listener as ListenerImplementation<*>).action, DebugInfo(opName, fileAndLine))
    }
}

public var debugCollector: DebugCollector? = null
