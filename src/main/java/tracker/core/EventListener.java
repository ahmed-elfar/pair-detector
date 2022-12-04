package tracker.core;

public interface EventListener<T> extends IsProcess {

    void onEvent(T t);

    void pollEvents();
}
