/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.general;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;

import com.intellij.util.JavaCoroutines;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

// #api242
public class AsyncVfsEventsPostProcessorCompat {
    private AsyncVfsEventsPostProcessorCompat(){

    }

    public static void addListener(Consumer<List<? extends VFileEvent>> listener, Disposable disposable, Project project) {
        AsyncVfsEventsPostProcessor.getInstance().addListener(new AsyncVfsEventsListener() {
            @Override
            public @Nullable Object filesChanged(@NotNull List<? extends VFileEvent> list, @NotNull Continuation<? super Unit> continuation) {
                return JavaCoroutines.suspendJava(javaContinuation -> {
                            listener.accept(list);
                            javaContinuation.resume(Unit.INSTANCE);
                        }
                        , continuation);
            }
        }, ((ComponentManagerEx) project).getCoroutineScope());
    }
}
