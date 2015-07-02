package sodium.impl

public class NeverStreamImpl<A> : StreamImpl<A>() {
    override val firings = emptyList<A>()
}
