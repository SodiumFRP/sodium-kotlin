package sodium;

public class Cell<A> {
	protected final Stream<A> str;
	A value;
	A valueUpdate;
	private Listener cleanup;
    protected Lazy<A> lazyInitValue;  // Used by LazyCell

	/**
	 * A cell with a constant value.
	 */
    public Cell(final A value)
    {
        str = new Stream<A>();
    	this.value = value;
    }

    Cell(final Stream<A> str, final A initValue)
    {
    	this.str = str;
        value = initValue;
    	Transaction.run(new Handler<Transaction>() {
    		@Override
            public void run(final Transaction trans1) {
                cleanup = str.listen(Node.NULL, trans1, new TransactionHandler<A>() {
	    			@Override
                    public void run(final Transaction trans2, final A a) {
			    		if (valueUpdate == null) {
			    			trans2.last(new Runnable() {
			    				@Override
                                public void run() {
                                    value = valueUpdate;
                                    lazyInitValue = null;
                                    valueUpdate = null;
				    			}
			    			});
			    		}
                        valueUpdate = a;
			    	}
	    		}, false);
    		}
    	});
    }

    /**
     * @return The value including any updates that have happened in this transaction.
     */
    final A newValue()
    {
    	return valueUpdate == null ? sampleNoTrans() :  valueUpdate;
    }

    /**
     * Sample the cell's current value.
     *
     * This should generally be avoided in favour of value().listen(..) so you don't
     * miss any updates, but in many circumstances it makes sense.
     *
     * It can be best to use it inside an explicit transaction (using Transaction.run()).
     * For example, a b.sample() inside an explicit transaction along with a
     * b.updates().listen(..) will capture the current value and any updates without risk
     * of missing any in between.
     */
    public final A sample()
    {
        return Transaction.apply(new Lambda1<Transaction, A>() {
        	@Override
            public A apply(final Transaction trans) {
        		return sampleNoTrans();
        	}
        });
    }

    private static class LazySample<A> {
        LazySample(final Cell<A> cell) {
            this.cell = cell;
        }
        Cell<A> cell;
        boolean hasValue;
        A value;
    }

    /**
     * A variant of sample() that works for CellLoops when they haven't been looped yet.
     */
    public final Lazy<A> sampleLazy() {
        final Cell<A> me = this;
        return Transaction.apply(new Lambda1<Transaction, Lazy<A>>() {
        	@Override
            public Lazy<A> apply(final Transaction trans) {
        	    return me.sampleLazy(trans);
            }
        });
    }

    final Lazy<A> sampleLazy(final Transaction trans) {
        final Cell<A> me = this;
        final LazySample<A> s = new LazySample<A>(me);
        trans.last(new Runnable() {
            @Override
            public void run() {
                s.value = me.valueUpdate != null ? me.valueUpdate : me.sampleNoTrans();
                s.hasValue = true;
                s.cell = null;
            }
        });
        return new Lazy<A>(new Lambda0<A>() {
            @Override
            public A apply() {
                if (s.hasValue)
                    return s.value;
                else
                    return s.cell.sample();
            }
        });
    }

    protected A sampleNoTrans()
    {
        return value;
    }

    final Stream<A> updates(final Transaction trans)
    {
        return str.lastFiringOnly(trans);
    }

    final Stream<A> value(final Transaction trans1)
    {
    	final StreamSink<Unit> sSpark = new StreamSink<Unit>();
        trans1.prioritized(sSpark.node, new Handler<Transaction>() {
            @Override
            public void run(final Transaction trans2) {
                sSpark.send(trans2, Unit.UNIT);
            }
        });
    	final Stream<A> sInitial = sSpark.snapshot(this);
        return sInitial.merge(updates(trans1));
    }

    /**
     * Transform the cell's value according to the supplied function.
     */
	public final <B> Cell<B> map(final Lambda1<A,B> f)
	{
		return Transaction.apply(new Lambda1<Transaction, Cell<B>>() {
			@Override
            public Cell<B> apply(final Transaction trans) {
                return updates(trans).map(f).holdLazy(trans, sampleLazy(trans).map(f));
            }
        });
	}

	/**
	 * Lift a binary function into cells.
	 */
	public static <A,B,C> Cell<C> lift(final Lambda2<A,B,C> f, final Cell<A> a, final Cell<B> b)
	{
		final Lambda1<A, Lambda1<B,C>> ffa = new Lambda1<A, Lambda1<B,C>>() {
			@Override
            public Lambda1<B,C> apply(final A aa) {
				return new Lambda1<B,C>() {
					@Override
                    public C apply(final B bb) {
						return f.apply(aa,bb);
					}
				};
			}
		};
		final Cell<Lambda1<B,C>> bf = a.map(ffa);
		return apply(bf, b);
	}

	/**
	 * Lift a ternary function into cells.
	 */
	public static <A,B,C,D> Cell<D> lift(final Lambda3<A,B,C,D> f, final Cell<A> a, final Cell<B> b, final Cell<C> c)
	{
		final Lambda1<A, Lambda1<B, Lambda1<C,D>>> ffa = new Lambda1<A, Lambda1<B, Lambda1<C,D>>>() {
			@Override
            public Lambda1<B, Lambda1<C,D>> apply(final A aa) {
				return new Lambda1<B, Lambda1<C,D>>() {
					@Override
                    public Lambda1<C,D> apply(final B bb) {
						return new Lambda1<C,D>() {
							@Override
                            public D apply(final C cc) {
								return f.apply(aa,bb,cc);
							}
						};
					}
				};
			}
		};
		final Cell<Lambda1<B, Lambda1<C, D>>> bf = a.map(ffa);
		return apply(apply(bf, b), c);
	}

	/**
	 * Lift a quaternary function into cells.
	 */
	public static <A,B,C,D,E> Cell<E> lift(final Lambda4<A,B,C,D,E> f, final Cell<A> a, final Cell<B> b, final Cell<C> c, final Cell<D> d)
	{
		final Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D,E>>>> ffa = new Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D,E>>>>() {
			@Override
            public Lambda1<B, Lambda1<C, Lambda1<D,E>>> apply(final A aa) {
				return new Lambda1<B, Lambda1<C, Lambda1<D,E>>>() {
					@Override
                    public Lambda1<C, Lambda1<D, E>> apply(final B bb) {
						return new Lambda1<C, Lambda1<D,E>>() {
							@Override
                            public Lambda1<D,E> apply(final C cc) {
                                return new Lambda1<D, E>() {
                                    @Override
                                    public E apply(final D dd) {
                                        return f.apply(aa,bb,cc,dd);
                                    }
                                };
							}
						};
					}
				};
			}
		};
		final Cell<Lambda1<B, Lambda1<C, Lambda1<D, E>>>> bf = a.map(ffa);
		return apply(apply(apply(bf, b), c), d);
	}

	/**
	 * Lift a 5-argument function into cells.
	 */
	public static <A,B,C,D,E,F> Cell<F> lift(final Lambda5<A,B,C,D,E,F> fn, final Cell<A> a, final Cell<B> b, final Cell<C> c, final Cell<D> d, final Cell<E> e)
	{
		final Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, F>>>>> ffa = new Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D,Lambda1<E, F>>>>>() {
			@Override
            public Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, F>>>> apply(final A aa) {
				return new Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, F>>>>() {
					@Override
                    public Lambda1<C, Lambda1<D, Lambda1<E, F>>> apply(final B bb) {
						return new Lambda1<C, Lambda1<D, Lambda1<E, F>>>() {
							@Override
                            public Lambda1<D, Lambda1<E, F>> apply(final C cc) {
                                return new Lambda1<D, Lambda1<E, F>>() {
                                    @Override
                                    public Lambda1<E, F> apply(final D dd) {
                                        return new Lambda1<E, F>() {
                                            @Override
                                            public F apply(final E ee) {
                                                return fn.apply(aa,bb,cc,dd,ee);
                                            }
                                        };
                                    }
                                };
							}
						};
					}
				};
			}
		};
		final Cell<Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, F>>>>> bf = a.map(ffa);
		return apply(apply(apply(apply(bf, b), c), d), e);
	}

	/**
	 * Lift a 6-argument function into cells.
	 */
	public static <A,B,C,D,E,F,G> Cell<G> lift(final Lambda6<A,B,C,D,E,F,G> fn, final Cell<A> a, final Cell<B> b, final Cell<C> c, final Cell<D> d, final Cell<E> e, final Cell<F> f)
	{
		final Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>>> ffa = new Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D,Lambda1<E, Lambda1<F, G>>>>>>() {
			@Override
            public Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>> apply(final A aa) {
				return new Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>>() {
					@Override
                    public Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>> apply(final B bb) {
						return new Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>() {
							@Override
                            public Lambda1<D, Lambda1<E, Lambda1<F, G>>> apply(final C cc) {
                                return new Lambda1<D, Lambda1<E, Lambda1<F, G>>>() {
                                    @Override
                                    public Lambda1<E, Lambda1<F, G>> apply(final D dd) {
                                        return new Lambda1<E, Lambda1<F, G>>() {
                                            @Override
                                            public Lambda1<F, G> apply(final E ee) {
                                                return new Lambda1<F, G>() {
                                                    @Override
                                                    public G apply(final F ff) {
                                                        return fn.apply(aa,bb,cc,dd,ee,ff);
                                                    }
                                                };
                                            }
                                        };
                                    }
                                };
							}
						};
					}
				};
			}
		};
		final Cell<Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>>> bf = a.map(ffa);
		return apply(apply(apply(apply(apply(bf, b), c), d), e), f);
	}

	/**
	 * Apply a value inside a cell to a function inside a cell. This is the
	 * primitive for all function lifting.
	 */
	public static <A,B> Cell<B> apply(final Cell<Lambda1<A,B>> bf, final Cell<A> ba)
	{
    	return Transaction.apply(new Lambda1<Transaction, Cell<B>>() {
    		@Override
            public Cell<B> apply(final Transaction trans0) {
                final StreamSink<B> out = new StreamSink<B>();

                class ApplyHandler implements Handler<Transaction> {
                    ApplyHandler(final Transaction trans0) {
                    }
                    Lambda1<A,B> f;
                    A a;
                    @Override
                    public void run(final Transaction trans1) {
                        trans1.prioritized(out.node, new Handler<Transaction>() {
                            @Override
                            public void run(final Transaction trans2) {
                                out.send(trans2, f.apply(a));
                            }
                        });
                    }
                }

                final Node out_target = out.node;
                final Node in_target = new Node(0);
                final Node.Target[] node_target_ = new Node.Target[1];
                in_target.linkTo(null, out_target, node_target_);
                final Node.Target node_target = node_target_[0];
                final ApplyHandler h = new ApplyHandler(trans0);
                final Listener l1 = bf.value(trans0).listen_(in_target, new TransactionHandler<Lambda1<A,B>>() {
                    @Override
                    public void run(final Transaction trans1, final Lambda1<A,B> f) {
                        h.f = f;
                        if (h.a != null)
                            h.run(trans1);
                    }
                });
                final Listener l2 = ba.value(trans0).listen_(in_target, new TransactionHandler<A>() {
                    @Override
                    public void run(final Transaction trans1, final A a) {
                        h.a = a;
                        if (h.f != null)
                            h.run(trans1);
                    }
                });
                return out.unsafeAddCleanup(l1).unsafeAddCleanup(l2).unsafeAddCleanup(
                    new Listener() {
                        @Override
                        public void unlisten() {
                            in_target.unlinkTo(node_target);
                        }
                    }).holdLazy(new Lazy<B>(new Lambda0<B>() {
                        @Override
                        public B apply() {
                            return bf.sampleNoTrans().apply(ba.sampleNoTrans());
                        }
                    }));
            }
        });
	}

	/**
	 * Unwrap a cell inside another cell to give a time-varying cell implementation.
	 */
	public static <A> Cell<A> switchC(final Cell<Cell<A>> bba)
	{
	    return Transaction.apply(new Lambda1<Transaction, Cell<A>>() {
	        @Override
            public Cell<A> apply(final Transaction trans0) {
                final Lazy<A> za = bba.sampleLazy().map(new Lambda1<Cell<A>, A>() {
                    @Override
                    public A apply(final Cell<A> ba) {return ba.sample();}
                });
                final StreamSink<A> out = new StreamSink<A>();
                final TransactionHandler<Cell<A>> h = new TransactionHandler<Cell<A>>() {
                    private Listener currentListener;
                    @Override
                    public void run(final Transaction trans2, final Cell<A> ba) {
                        // Note: If any switch takes place during a transaction, then the
                        // value().listen will always cause a sample to be fetched from the
                        // one we just switched to. The caller will be fetching our output
                        // using value().listen, and value() throws away all firings except
                        // for the last one. Therefore, anything from the old input behaviour
                        // that might have happened during this transaction will be suppressed.
                        if (currentListener != null)
                            currentListener.unlisten();
                        currentListener = ba.value(trans2).listen(out.node, trans2, new TransactionHandler<A>() {
                            @Override
                            public void run(final Transaction trans3, final A a) {
                                out.send(trans3, a);
                            }
                        }, false);
                    }

                    @Override
                    protected void finalize() throws Throwable {
                        if (currentListener != null)
                            currentListener.unlisten();
                    }
                };
                final Listener l1 = bba.value(trans0).listen_(out.node, h);
                return out.unsafeAddCleanup(l1).holdLazy(za);
            }
        });
	}

	/**
	 * Unwrap a stream inside a cell to give a time-varying stream implementation.
	 */
	public static <A> Stream<A> switchS(final Cell<Stream<A>> bea)
	{
        return Transaction.apply(new Lambda1<Transaction, Stream<A>>() {
        	@Override
            public Stream<A> apply(final Transaction trans) {
                return switchS(trans, bea);
        	}
        });
    }

	private static <A> Stream<A> switchS(final Transaction trans1, final Cell<Stream<A>> bea)
	{
        final StreamSink<A> out = new StreamSink<A>();
        final TransactionHandler<A> h2 = new TransactionHandler<A>() {
        	@Override
            public void run(final Transaction trans2, final A a) {
	            out.send(trans2, a);
	        }
        };
        final TransactionHandler<Stream<A>> h1 = new TransactionHandler<Stream<A>>() {
            private Listener currentListener = bea.sampleNoTrans().listen(out.node, trans1, h2, false);

            @Override
            public void run(final Transaction trans2, final Stream<A> ea) {
                trans2.last(new Runnable() {
                	@Override
                    public void run() {
	                    if (currentListener != null)
	                        currentListener.unlisten();
	                    currentListener = ea.listen(out.node, trans2, h2, true);
	                }
                });
            }

            @Override
            protected void finalize() throws Throwable {
                if (currentListener != null)
                    currentListener.unlisten();
            }
        };
        final Listener l1 = bea.updates(trans1).listen(out.node, trans1, h1, false);
        return out.unsafeAddCleanup(l1);
	}

    /**
     * Transform a cell with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     */
    public final <B,S> Cell<B> collect(final S initState, final Lambda2<A, S, Tuple2<B, S>> f)
    {
        return collect(new Lazy<S>(initState), f);
    }

    /**
     * Transform a cell with a generalized state loop (a mealy machine). The function
     * is passed the input and the old state and returns the new state and output value.
     * Variant that takes a lazy initial state.
     */
    public final <B,S> Cell<B> collect(final Lazy<S> initState, final Lambda2<A, S, Tuple2<B, S>> f)
    {
        return Transaction.apply(new Lambda1<Transaction, Cell<B>>() {
            @Override
            public Cell<B> apply(final Transaction trans0) {
                final Stream<A> ea = updates(trans0).coalesce(new Lambda2<A, A, A>() {
                    @Override
                    public A apply(final A fst, final A snd) { return snd; }
                });
                final Lazy<Tuple2<B, S>> zbs = Lazy.lift(
                        f, sampleLazy(), initState);
                final StreamLoop<Tuple2<B, S>> ebs = new StreamLoop<Tuple2<B, S>>();
                final Cell<Tuple2<B, S>> bbs = ebs.holdLazy(zbs);
                final Cell<S> bs = bbs.map(new Lambda1<Tuple2<B, S>, S>() {
                    @Override
                    public S apply(final Tuple2<B, S> x) {
                        return x.b;
                    }
                });
                final Stream<Tuple2<B, S>> ebs_out = ea.snapshot(bs, f);
                ebs.loop(ebs_out);
                return bbs.map(new Lambda1<Tuple2<B, S>, B>() {
                    @Override
                    public B apply(final Tuple2<B, S> x) {
                        return x.a;
                    }
                });
            }
        });
    }

	@Override
	protected void finalize() throws Throwable {
	    if (cleanup != null)
            cleanup.unlisten();
	}

	/**
	 * Listen for firings of this stream. The returned Listener has an unlisten()
	 * method to cause the listener to be removed. This is the observer pattern.
     */
	public final Listener listen(final Handler<A> action) {
        return Transaction.apply(new Lambda1<Transaction, Listener>() {
        	@Override
            public Listener apply(final Transaction trans) {
                return value(trans).listen(action);
			}
		});
	}
}
