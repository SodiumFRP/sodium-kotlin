package sodium;

import kotlin.jvm.functions.Function0;
import sodium.impl.CellImpl;
import sodium.impl.StreamImpl;

// TODO: kotlin version of this class not generates super() call coz of bug.
public class LazyCell<A> extends CellImpl<A> {

    Function0<Event<A>> lazyValue;

    public LazyCell(final StreamImpl<A> stream, final boolean lo, final Function0<Event<A>> lazyValue) {
        super(null, stream, lo); // < Here!
        this.lazyValue = lazyValue;
    }

    @Override
    public Event<A> sampleNoTrans() {
        final Function0<Event<A>> lazyValue = this.lazyValue;
        final Event<A> value = getValue();
        final Event<A> newValue;

        if (value == null && lazyValue != null) {
            newValue = lazyValue.invoke();
            setValue(newValue);
            this.lazyValue = null;
        } else {
            newValue = value;
        }

        return newValue;
    }

    @Override
    protected void setupValue() {
        super.setupValue();
        lazyValue = null;
    }
}
