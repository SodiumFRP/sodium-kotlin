package sodium.impl

import sodium.Cell
import sodium.Listener
import sodium.Stream
import java.util.HashSet
import java.util.WeakHashMap

private fun dump(sb: Appendable, depth: Int, node: Node<*>): Unit = with(sb) {
    node.listeners.forEach {
        direction(formatNode(node), formatTarget(it))
        direction(formatTarget(it), formatNode(it.node))

        val action = it.action.get()
        if (action != null) {
            append('\n')
            single(formatTarget(it))
            append(" -> ")
            formatAction(action)
        }

        dump(sb, depth + 2, it.node)
    }

    Unit
}

private fun Appendable.declareNodeStyle(node: Node<*>) {
    append("\n{node [shape=box;style=filled;color=lightgrey;]")
    val allNodes = HashSet<Node<*>>()
    getAllNodes(allNodes, node)
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

public fun dump(sb: Appendable, node: Node<*>): Unit = with(sb) {
    append('\n')
    append("digraph G {")
    declareNodeStyle(node)
    //dumpTargets(sb, 2, Node.Target<Any>(null, node))
    dump(sb, 2, node)
    append("\n}")
    Unit
}

private fun Appendable.spaces(depth: Int) {
    for (i in 1 rangeTo depth) {
        append(' ')
    }
}


public fun dump(cell: Cell<*>) {
    dump(System.out, (cell as CellImpl<*>).stream.node)
}

public fun dump(stream: Stream<*>) {
    dump(System.out, (stream as StreamImpl<*>).node)
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
