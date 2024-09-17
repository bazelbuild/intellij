package com.google.idea.sdkcompat.vfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.JavaCoroutines;
import com.intellij.vfs.AsyncVfsEventsListener;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class AsyncVfsEventsListenerCompat implements AsyncVfsEventsListener {
    private final Consumer<List<? extends VFileEvent>> listener;

    public AsyncVfsEventsListenerCompat(Consumer<List<? extends VFileEvent>> listener) {
        this.listener = listener;
    }

    @Override
    public @Nullable Object filesChanged(@NotNull List<? extends VFileEvent> list, @NotNull Continuation<? super Unit> $completion) {
        return JavaCoroutines.suspendJava(continuation -> listener.accept(list), $completion);
    }
}
