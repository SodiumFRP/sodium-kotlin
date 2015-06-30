package sodium;

import sodium.impl.CellImpl;

// TODO: kotlin version of this class not generates super() call coz of bug.
public class LazyCell<A> extends CellImpl<A> {

    Lazy<A> lazyValue;

    public LazyCell(final Stream<A> stream, final Lazy<A> lazyValue) {
        super(null, stream); // < Here!
        this.lazyValue = lazyValue;
    }

    @Override
    public A sampleNoTrans() {
        final Lazy<A> lazyValue = this.lazyValue;
        final A value = getValue();
        final A newValue;

        if (value == null && lazyValue != null) {
            newValue = lazyValue.get();
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
