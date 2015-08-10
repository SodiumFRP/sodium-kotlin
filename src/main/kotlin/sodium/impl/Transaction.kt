package sodium.impl

import java.util.ArrayList
import java.util.HashSet
import java.util.PriorityQueue

public class Transaction {
    private val prioritizedQ = PriorityQueue<Entry>()
    private val entries = HashSet<Entry>()
    private val lastQ = ArrayList<() -> Unit>()
    private var postQ: ArrayList<() -> Unit>? = null
    private var began: Boolean = false
    // True if we need to re-generate the priority queue.
    var toRegen: Boolean = false

    public fun prioritized(node: Node<*>, action: (Transaction) -> Unit) {
        val e = Entry(node, action)
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
        val pq = postQ
        val list = if (pq == null) {
            val list = ArrayList<() -> Unit>()
            postQ = list
            list
        } else {
            pq
        }

        list.add(action)
    }

    /**
     * If the priority queue has entries in it when we modify any of the nodes'
     * ranks, then we need to re-generate it to make sure it's up-to-date.
     */
    private fun checkRegen() {
        if (toRegen) {
            toRegen = false
            prioritizedQ.clear()
            for (e in entries) {
                prioritizedQ.add(e)
            }
        }
    }

    public fun close() {
        while (true) {
            checkRegen()
            if (prioritizedQ.isEmpty())
                break
            val e = prioritizedQ.remove()
            entries.remove(e)
            e.action(this)
        }

        for (action in lastQ) {
            action()
        }
        lastQ.clear()
    }

    public fun onPostTx() {
        val pq = postQ
        if (pq != null) {
            postQ = null
            for (action in pq) {
                action()
            }
        }
    }

	private class Entry(val node: Node<*>, val action: (Transaction) -> Unit) : Comparable<Entry> {
		private val seq: Long = nextSeq++

		override fun compareTo(other: Entry): Int {
			val answer = node.compareTo(other.node)
			return if (answer == 0) {
				// Same rank: preserve chronological sequence.
                when {
                    seq < other.seq -> -1
                    seq > other.seq -> 1
                    else -> 0
                }
			} else {
                answer
            }
		}

		companion object {
			private var nextSeq: Long = 0
		}
	}

    companion object {
        // Coarse-grained lock that's held during the whole transaction.
        public val transactionLock: Any = Any()
        // Fine-grained lock that protects listeners and nodes.
        val listenersLock = Any()

        public var currentTransaction: Transaction = Transaction()
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

        public fun begin(): Boolean {
            val transWas = currentTransaction
            return if (transWas.began) {
                false
            } else {
                transWas.began = true

                if (!runningOnStartHooks) {
                    runningOnStartHooks = true
                    try {
                        for (r in onStartHooks) {
                            r.run()
                        }
                    } finally {
                        runningOnStartHooks = false
                    }
                }

                true
            }
        }

        public fun end() {
            val tx = currentTransaction
            currentTransaction = Transaction()
            tx.onPostTx()
        }

        /**
         * Run the specified code inside a single transaction, with the contained
         * code returning a value of the parameter type A.
         *
         * In most cases this is not needed, because all APIs will create their own
         * transaction automatically. It is useful where you want to run multiple
         * reactive operations atomically.
         */
        public fun <A> apply(code: (Transaction) -> A): A = synchronized (transactionLock) {
            val needClose = begin()
            try {
                val result = code(currentTransaction)
                if (needClose) {
                    currentTransaction.close()
                }
                result
            } finally {
                if (needClose) {
                    end()
                }
            }
        }
    }
}
