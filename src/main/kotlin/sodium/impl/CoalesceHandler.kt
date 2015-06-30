package sodium.impl

import sodium.Transaction

class CoalesceHandler<A>(private val f: (A, A) -> A, private val out: StreamWithSend<A>) : (Transaction, A) -> Unit {
    private var accumValid: Boolean = false
    private var accum: A = null

    override fun invoke(transaction: Transaction, a: A) {
        if (accumValid) {
            accum = f(accum, a)
        } else {
            transaction.prioritized(out.node) {
                out.send(it, accum)
                accumValid = false
                accum = null
            }
            accum = a
            accumValid = true
        }
    }
}
