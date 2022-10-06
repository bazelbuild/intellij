/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ijlib.testonly.adapters;

import com.google.common.util.concurrent.ForwardingFuture.SimpleForwardingFuture;
import java.util.concurrent.Executor;

/**
 * Class to bridge the gap between equivalent but repackaged versions of ListenableFuture.
 *
 * <p>When running tests, the version of guava from the IntelliJ library jars is repackaged to avoid
 * clashes with the version in tests. This class helps bridge the gap. For use in tests only, since
 * the repackaging does not happen at runtime for prod code.
 */
public class ListenableFutureAdapter {

  static class IjLibListenableFuture<T> extends SimpleForwardingFuture<T>
      implements ijlib.testonly.com.google.common.util.concurrent.ListenableFuture<T> {
    public IjLibListenableFuture(com.google.common.util.concurrent.ListenableFuture<T> wrapped) {
      super(wrapped);
    }

    public void addListener(Runnable listener, Executor executor) {
      ((com.google.common.util.concurrent.ListenableFuture<T>) delegate())
          .addListener(listener, executor);
    }
  }

  public static <T> ijlib.testonly.com.google.common.util.concurrent.ListenableFuture<T> wrap(
      com.google.common.util.concurrent.ListenableFuture<T> future) {
    return new IjLibListenableFuture<T>(future);
  }
}
