package dev.larp.client.event;

@FunctionalInterface
public interface Subscription {
    void cancel();
}
