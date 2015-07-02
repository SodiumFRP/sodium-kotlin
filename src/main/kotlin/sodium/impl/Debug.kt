package sodium.impl

import sodium.Cell
import sodium.Stream

public fun dump(sb: Appendable, depth: Int, node: Node<*>): Unit = with(sb) {
    append("Node(${node.rank})")

    node.listeners.forEach {
        append('\n')
        spaces(depth + 2)
        append(it.action.get()?.hashCode().toString())
        append('\n')
        spaces(depth + 2)
        dump(sb, depth + 2, it.node)
    }

    Unit
}

private fun Appendable.spaces(depth: Int) {
    for (i in 1 rangeTo depth) {
        append(' ')
    }
}


public fun dump(cell: Cell<*>) {
    dump(System.out, 0, (cell as CellImpl<*>).stream.node)
}

public fun dump(stream: Stream<*>) {
    dump(System.out, 0, (stream as StreamImpl<*>).node)
}
