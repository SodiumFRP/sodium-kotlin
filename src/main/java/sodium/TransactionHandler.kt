package sodium

public interface TransactionHandler<A> : (Transaction, A) -> Unit

