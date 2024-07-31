package com.google.idea.blaze.android.projectsystem;

import com.android.tools.idea.projectsystem.ClassContent;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * A cache for {@link ClassContent} objects mapped by fully qualified class name.
 */
public interface ClassContentCache {
  @Nullable ClassContent getClassContent(String fqcn);

  void putEntry(String fqcn, ClassContent classContent);

  void invalidate();

  static ClassContentCache getInstance(Project project) {
    return project.getService(ClassContentCacheImpl.class);
  }

  class Listener implements SyncListener {
    @Override
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
      //TODO: there may be more scenarios to invalidate the cache
      if (syncMode == SyncMode.FULL) {
        ClassContentCache.getInstance(project).invalidate();
      }
    }
  }
}
