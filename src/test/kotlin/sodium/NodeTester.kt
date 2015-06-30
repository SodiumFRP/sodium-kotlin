package sodium

import junit.framework.TestCase
import sodium.impl.Node

public class NodeTester : TestCase() {
    public fun testNode() {
        val a = Node<Any>(0)
        val b = Node<Any>(1)
        a.linkTo(null, b)
        TestCase.assertTrue(a.compareTo(b) < 0)
    }
}
