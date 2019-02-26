/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.io;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Reads file attributes from a list files in parallel. */
public class FileAttributeScanner {

  interface AttributeReader<T> {
    T getAttribute(File file);

    boolean isValid(T attribute);
  }

  public static <T> ImmutableMap<File, T> readAttributes(
      Iterable<File> fileList, AttributeReader<T> attributeReader, BlazeExecutor executor)
      throws InterruptedException, ExecutionException {
    List<ListenableFuture<FilePair<T>>> futures = Lists.newArrayList();
    for (File file : fileList) {
      futures.add(
          executor.submit(
              () -> {
                T attribute = attributeReader.getAttribute(file);
                if (attributeReader.isValid(attribute)) {
                  return new FilePair<>(file, attribute);
                }
                return null;
              }));
    }

    ImmutableMap.Builder<File, T> result = ImmutableMap.builder();
    for (FilePair<T> filePair : Futures.allAsList(futures).get()) {
      if (filePair != null) {
        result.put(filePair.file, filePair.attribute);
      }
    }
    return result.build();
  }

  private static class FilePair<T> {
    public final File file;
    public final T attribute;

    public FilePair(File file, T attribute) {
      this.file = file;
      this.attribute = attribute;
    }
  }
}
