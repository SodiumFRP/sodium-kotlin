package sodium;

class LazyCell<A> extends Cell<A> {
    LazyCell(final Stream<A> event, final Lazy<A> lazyInitValue) {
        super(event, null);
        setLazyInitValue(lazyInitValue);
    }

    @Override
    protected A sampleNoTrans()
    {
        if (getValue() == null && getLazyInitValue() != null) {
            setValue(lazyInitValue.get());
            lazyInitValue = null;
        }
        return getValue();
    }
}

