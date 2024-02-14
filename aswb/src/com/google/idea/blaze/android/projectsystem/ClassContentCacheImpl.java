package com.google.idea.blaze.android.projectsystem;

import com.android.tools.idea.projectsystem.ClassContent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ClassContentCacheImpl implements ClassContentCache {
  private final Cache<String, ClassContent> cache;


  public ClassContentCacheImpl() {
    cache = CacheBuilder.newBuilder()
        .softValues()
        .maximumSize(2500)
        .build();
  }

  @Override
  @Nullable
  public ClassContent getClassContent(String fqcn) {
    ClassContent content = cache.getIfPresent(fqcn);
    if (content != null && content.isUpToDate()) {
      return content;
    }
    return null;
  }

  @Override
  public void putEntry(String fqcn, ClassContent classContent) {
    cache.put(fqcn, classContent);
  }

  @Override
  public void invalidate() {
    cache.invalidateAll();
  }
}
