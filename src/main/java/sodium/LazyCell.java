package sodium;

import kotlin.jvm.functions.Function0;
import sodium.impl.CellImpl;
import sodium.impl.StreamImpl;

// TODO: kotlin version of this class not generates super() call coz of bug.
public class LazyCell<A> extends CellImpl<A> {

    Function0<A> lazyValue;

    public LazyCell(final Function0<A> lazyValue, final StreamImpl<A> stream) {
        super(null, stream); // < Here!
        this.lazyValue = lazyValue;
    }

    @Override
    public A sampleNoTrans() {
        final Function0<A> lazyValue = this.lazyValue;
        final A value = getValue();
        final A newValue;

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
