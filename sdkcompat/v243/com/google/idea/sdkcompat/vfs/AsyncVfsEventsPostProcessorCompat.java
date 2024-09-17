package com.google.idea.sdkcompat.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import kotlinx.coroutines.CoroutineScope;

public class AsyncVfsEventsPostProcessorCompat {
    private AsyncVfsEventsPostProcessorCompat() {
    }

    public static void addListener(AsyncVfsEventsListener listener, Disposable parentDisposable, CoroutineScope scope) {
        AsyncVfsEventsPostProcessor.getInstance()
                .addListener(listener, scope);
    }
}
