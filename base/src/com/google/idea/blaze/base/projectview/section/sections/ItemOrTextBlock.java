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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.common.base.Objects;
import java.io.Serializable;
import javax.annotation.Nullable;

/** Union of an item or comment block */
public class ItemOrTextBlock<T> implements Serializable {
  private static final long serialVersionUID = 2L;
  @Nullable public final T item;
  @Nullable public final TextBlock textBlock;

  private int lineIndex;

  public int getLineIndex() {
    return lineIndex;
  }

  public void setLineIndex(int lineIndex) {
    this.lineIndex = lineIndex;
  }

  public ItemOrTextBlock(T item, int lineIndex) {
    this(item, null, lineIndex);
  }

  public ItemOrTextBlock(TextBlock comment, int lineIndex) {
    this(null, comment, lineIndex);
  }

  private ItemOrTextBlock(@Nullable T item, @Nullable TextBlock comment, int lineIndex) {
    this.item = item;
    this.textBlock = comment;
    this.lineIndex = lineIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ItemOrTextBlock<?> that = (ItemOrTextBlock<?>) o;
    return Objects.equal(item, that.item) && Objects.equal(textBlock, that.textBlock) && Objects.equal(lineIndex, that.lineIndex);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(item, textBlock);
  }
}
