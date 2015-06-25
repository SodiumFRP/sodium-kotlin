package sodium

import java.util.ArrayList
import java.util.HashSet
import java.util.PriorityQueue

public class Transaction {

    // True if we need to re-generate the priority queue.
    var toRegen: Boolean = false
    private val prioritizedQ = PriorityQueue<Entry>()
    private val entries = HashSet<Entry>()
    private val lastQ = ArrayList<() -> Unit>()
    private var postQ: MutableList<() -> Unit>? = null

    public fun prioritized(rank: Node, action: (Transaction) -> Unit) {
        val e = Entry(rank, action)
        prioritizedQ.add(e)
        entries.add(e)
    }

    /**
     * Add an action to run after all prioritized() actions.
     */
    public fun last(action: () -> Unit) {
        lastQ.add(action)
    }

    /**
     * Add an action to run after all last() actions.
     */
    public fun post(action: () -> Unit) {
        if (postQ == null)
            postQ = ArrayList<() -> Unit>()
        postQ!!.add(action)
    }

    /**
     * If the priority queue has entries in it when we modify any of the nodes'
     * ranks, then we need to re-generate it to make sure it's up-to-date.
     */
    private fun checkRegen() {
        if (toRegen) {
            toRegen = false
            prioritizedQ.clear()
            for (e in entries)
                prioritizedQ.add(e)
        }
    }

    fun close() {
        while (true) {
            checkRegen()
            if (prioritizedQ.isEmpty()) break
            val e = prioritizedQ.remove()
            entries.remove(e)
            e.action(this)
        }
        for (action in lastQ)
            action()
        lastQ.clear()
        if (postQ != null) {
            for (action in postQ!!)
                action()
            postQ!!.clear()
        }
    }

	private class Entry(private val rank: Node, val action: (Transaction) -> Unit) : Comparable<Entry> {
		private val seq: Long

		init {
			seq = nextSeq++
		}

		override fun compareTo(other: Entry): Int {
			var answer = rank.compareTo(other.rank)
			if (answer == 0) {
				// Same rank: preserve chronological sequence.
				if (seq < other.seq)
					answer = -1
				else if (seq > other.seq) answer = 1
			}
			return answer
		}

		companion object {
			private var nextSeq: Long = 0
		}

	}

    companion object {
        // Coarse-grained lock that's held during the whole transaction.
        val transactionLock = Object()
        // Fine-grained lock that protects listeners and nodes.
        val listenersLock = Object()

        private var currentTransaction: Transaction? = null
        var inCallback: Int = 0
        private val onStartHooks = ArrayList<Runnable>()
        private var runningOnStartHooks: Boolean = false

        /**
         * Return the current transaction, or null if there isn't one.
         */
        public fun getCurrent(): Transaction? {
            synchronized (transactionLock) {
                return currentTransaction
            }
        }

        /**
         * Add a runnable that will be executed whenever a transaction is started.
         * That runnable may start transactions itself, which will not cause the
         * hooks to be run recursively.

         * The main use case of this is the implementation of a time/alarm system.
         */
        public fun onStart(r: Runnable) {
            synchronized (transactionLock) {
                onStartHooks.add(r)
            }
        }


        /**
         * Run the specified code inside a single transaction, with the contained
         * code returning a value of the parameter type A.

         * In most cases this is not needed, because all APIs will create their own
         * transaction automatically. It is useful where you want to run multiple
         * reactive operations atomically.
         */
        public fun <A> apply(code: (Transaction) -> A): A {
            synchronized (transactionLock) {
                // If we are already inside a transaction (which must be on the same
                // thread otherwise we wouldn't have acquired transactionLock), then
                // keep using that same transaction.
                val transWas = currentTransaction
                try {
                    return code(startIfNecessary())
                } finally {
                    if (transWas == null)
                        currentTransaction!!.close()
                    currentTransaction = transWas
                }
            }
        }

        private fun startIfNecessary(): Transaction {
            return currentTransaction ?: run {
                if (!runningOnStartHooks) {
                    runningOnStartHooks = true
                    try {
                        for (r in onStartHooks)
                            r.run()
                    } finally {
                        runningOnStartHooks = false
                    }
                }
                val transaction = Transaction()
                currentTransaction = transaction
                transaction
            }
        }
    }
}
