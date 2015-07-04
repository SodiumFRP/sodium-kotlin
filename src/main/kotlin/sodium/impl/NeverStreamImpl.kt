package sodium.impl

import sodium.Event

public class NeverStreamImpl<A> : StreamImpl<A>() {
    override val firings = emptyList<Event<A>>()
}
