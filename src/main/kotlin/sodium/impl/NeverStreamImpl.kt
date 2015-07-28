package sodium.impl

import sodium.Event

public class NeverStreamImpl<A> : StreamImpl<A>() {
    override var firings: Event<A>? = null
}
