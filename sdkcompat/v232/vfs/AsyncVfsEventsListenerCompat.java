package com.google.idea.sdkcompat.vfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.vfs.AsyncVfsEventsListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public class AsyncVfsEventsListenerCompat implements AsyncVfsEventsListener {
    private final Consumer<List<? extends VFileEvent>> listener;

    public AsyncVfsEventsListenerCompat(Consumer<List<? extends VFileEvent>> listener) {
        this.listener = listener;
    }

    @Override
    public void filesChanged(@NotNull List<? extends @NotNull VFileEvent> list) {
        listener.accept(list);
    }
}
