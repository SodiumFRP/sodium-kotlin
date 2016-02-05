package sodium.impl

import sodium.Event

class NeverStreamImpl<A> : StreamImpl<A>() {
    override var firings: Event<A>? = null
}
