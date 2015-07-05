package sodium

import org.junit.Assert

object Util {
    public fun assertThrows(body: () -> Unit) {
        var ex: Exception? = null

        try {
            body()
        } catch (e: Exception) {
            ex = e
        }

        if (ex == null) {
            Assert.fail("Exception expected.")
        }
    }
}
