package sodium;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public final class Transaction {
    // Coarse-grained lock that's held during the whole transaction.
    static final Object transactionLock = new Object();
    // Fine-grained lock that protects listeners and nodes.
    static final Object listenersLock = new Object();

    // True if we need to re-generate the priority queue.
    boolean toRegen;

	private static class Entry implements Comparable<Entry> {
		private final Node rank;
		private final Handler<Transaction> action;
		private static long nextSeq;
		private final long seq;

		public Entry(Node rank, Handler<Transaction> action) {
			this.rank = rank;
			this.action = action;
			seq = nextSeq++;
		}

		@Override
		public int compareTo(Entry o) {
			int answer = rank.compareTo(o.rank);
			if (answer == 0) {  // Same rank: preserve chronological sequence.
				if (seq < o.seq) answer = -1; else
				if (seq > o.seq) answer = 1;
			}
			return answer;
		}

	}

	private final PriorityQueue<Entry> prioritizedQ = new PriorityQueue<Entry>();
	private final Set<Entry> entries = new HashSet<Entry>();
	private final List<Runnable> lastQ = new ArrayList<Runnable>();
	private List<Runnable> postQ;

	Transaction() {
	}

	private static Transaction currentTransaction;
    static int inCallback;
    private static final List<Runnable> onStartHooks = new ArrayList<Runnable>();
    private static boolean runningOnStartHooks;

	/**
	 * Return the current transaction, or null if there isn't one.
	 */
	public static Transaction getCurrentTransaction() {
        synchronized (transactionLock) {
            return currentTransaction;
        }
	}

	/**
	 * Run the specified code inside a single transaction.
	 *
	 * In most cases this is not needed, because all APIs will create their own
	 * transaction automatically. It is useful where you want to run multiple
	 * reactive operations atomically.
	 */
	public static void runVoid(Runnable code) {
        synchronized (transactionLock) {
            // If we are already inside a transaction (which must be on the same
            // thread otherwise we wouldn't have acquired transactionLock), then
            // keep using that same transaction.
            Transaction transWas = currentTransaction;
            try {
                startIfNecessary();
                code.run();
            } finally {
                if (transWas == null)
                    currentTransaction.close();
                currentTransaction = transWas;
            }
        }
	}

	/**
	 * Run the specified code inside a single transaction, with the contained
	 * code returning a value of the parameter type A.
	 *
	 * In most cases this is not needed, because all APIs will create their own
	 * transaction automatically. It is useful where you want to run multiple
	 * reactive operations atomically.
	 */
	public static <A> A run(Lambda0<A> code) {
        synchronized (transactionLock) {
            // If we are already inside a transaction (which must be on the same
            // thread otherwise we wouldn't have acquired transactionLock), then
            // keep using that same transaction.
            Transaction transWas = currentTransaction;
            try {
                startIfNecessary();
                return code.apply();
            } finally {
                if (transWas == null)
                    currentTransaction.close();
                currentTransaction = transWas;
            }
        }
	}

	public static void run(Handler<Transaction> code) {
        synchronized (transactionLock) {
            // If we are already inside a transaction (which must be on the same
            // thread otherwise we wouldn't have acquired transactionLock), then
            // keep using that same transaction.
            Transaction transWas = currentTransaction;
            try {
                startIfNecessary();
                code.run(currentTransaction);
            } finally {
                if (transWas == null)
                    currentTransaction.close();
                currentTransaction = transWas;
            }
        }
	}

	/**
	 * Add a runnable that will be executed whenever a transaction is started.
	 * That runnable may start transactions itself, which will not cause the
	 * hooks to be run recursively.
	 *
	 * The main use case of this is the implementation of a time/alarm system.
	 */
	public static void onStart(Runnable r) {
        synchronized (transactionLock) {
            onStartHooks.add(r);
        }
	}

	static <A> A apply(Lambda1<Transaction, A> code) {
        synchronized (transactionLock) {
            // If we are already inside a transaction (which must be on the same
            // thread otherwise we wouldn't have acquired transactionLock), then
            // keep using that same transaction.
            Transaction transWas = currentTransaction;
            try {
                startIfNecessary();
                return code.apply(currentTransaction);
            } finally {
                if (transWas == null)
                    currentTransaction.close();
                currentTransaction = transWas;
            }
        }
	}

	private static void startIfNecessary() {
        if (currentTransaction == null) {
            if (!runningOnStartHooks) {
                runningOnStartHooks = true;
                try {
                    for (Runnable r : onStartHooks)
                        r.run();
                }
                finally {
                    runningOnStartHooks = false;
                }
            }
            currentTransaction = new Transaction();
        }
	}

	public void prioritized(Node rank, Handler<Transaction> action) {
	    Entry e = new Entry(rank, action);
		prioritizedQ.add(e);
		entries.add(e);
	}

	/**
     * Add an action to run after all prioritized() actions.
     */
	public void last(Runnable action) {
	    lastQ.add(action);
	}

	/**
     * Add an action to run after all last() actions.
     */
	public void post(Runnable action) {
	    if (postQ == null)
	        postQ = new ArrayList<Runnable>();
	    postQ.add(action);
	}

	/**
	 * If the priority queue has entries in it when we modify any of the nodes'
	 * ranks, then we need to re-generate it to make sure it's up-to-date.
	 */
	private void checkRegen()
	{
	    if (toRegen) {
	        toRegen = false;
	        prioritizedQ.clear();
	        for (Entry e : entries)
	            prioritizedQ.add(e);
	    }
	}

	void close() {
	    while (true) {
	        checkRegen();
		    if (prioritizedQ.isEmpty()) break;
		    Entry e = prioritizedQ.remove();
		    entries.remove(e);
			e.action.run(this);
		}
		for (Runnable action : lastQ)
			action.run();
		lastQ.clear();
		if (postQ != null) {
            for (Runnable action : postQ)
                action.run();
            postQ.clear();
		}
	}
}
