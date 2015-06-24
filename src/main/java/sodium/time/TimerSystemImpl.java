package sodium.time;

public interface TimerSystemImpl<T> {
    Timer setTimer(T t, Runnable callback);
    void runTimersTo(T t);
    T now();
}

