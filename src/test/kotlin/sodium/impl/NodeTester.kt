package sodium.impl

import junit.framework.TestCase

class NodeTester : TestCase() {
    fun testNode() {
        val a = Node<Any>(0)
        val b = Node<Any>(1)
        a.link(b, null)
        TestCase.assertTrue(a.compareTo(b) < 0)
    }
}
