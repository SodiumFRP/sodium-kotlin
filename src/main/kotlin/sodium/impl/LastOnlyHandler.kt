package sodium.impl

import sodium.Event

class LastOnlyHandler<A>(val out: StreamWithSend<A>, val firings: List<Event<A>>) : (Transaction, Event<A>) -> Unit {
    private var notSent: Boolean = true

    override fun invoke(tx: Transaction, event: Event<A>) {
        if (notSent) {
            notSent = false
            tx.prioritized(out.node) {
                out.send(it, firings.last())
                notSent = true
            }
        }
    }
}
