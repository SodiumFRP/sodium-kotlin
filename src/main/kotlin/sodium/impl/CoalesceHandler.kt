package sodium.impl

import sodium.Error
import sodium.Event
import sodium.Value

class CoalesceHandler<A>(
        private val transformation: (Event<A>, Event<A>) -> A,
        private val out: StreamWithSend<A>) : (Transaction, Event<A>) -> Unit {
    private var accumulator: Event<A>? = null

    override fun invoke(transaction: Transaction, a: Event<A>) {
        val acc = accumulator
        if (acc != null) {
            try {
                accumulator = Value(transformation(acc, a))
            } catch (e: Exception) {
                accumulator = Error(e)
            }
        } else {
            transaction.prioritized(out.node) {
                val acc1 = accumulator
                if (acc1 != null) {
                    out.send(it, acc1)
                    accumulator = null
                }
            }
            accumulator = a
        }
    }
}
