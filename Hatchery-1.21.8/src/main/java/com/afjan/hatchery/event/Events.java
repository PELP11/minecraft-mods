package com.afjan.hatchery.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Game-bus listeners; a Predicate listener cancels the event by returning true. */
public final class Events {
    private Events() {
    }

    public static <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        listen(EventPriority.NORMAL, type, listener);
    }

    public static <E extends Event> void listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
        NeoForge.EVENT_BUS.addListener(priority, false, type, listener);
    }

    public static <E extends Event & ICancellableEvent> void listen(Class<E> type, Predicate<E> listener) {
        listen(EventPriority.NORMAL, type, listener);
    }

    public static <E extends Event & ICancellableEvent> void listen(EventPriority priority, Class<E> type, Predicate<E> listener) {
        NeoForge.EVENT_BUS.addListener(priority, false, type, event -> {
            if (listener.test(event)) event.setCanceled(true);
        });
    }
}
