/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.util.LockCriticalSection
import com.google.idea.blaze.base.util.LockCriticalSection.TryLockResult.Acquired
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Computes a cache on the project data.  */
@Service(Service.Level.PROJECT)
class SyncCache(private val project: Project, private val scope: CoroutineScope) {
  /** Computes a value based on the sync project data.  */
  interface SyncCacheComputable<T> {
    fun compute(project: Project, projectData: BlazeProjectData): T?
  }

  private data class Data(val cache: MutableMap<Any, Any?>)

  private val data = Data(Maps.newConcurrentMap<Any, Any>())
  private val section = LockCriticalSection<Data>(data)

  /**
   * Tries to get a value from the cache. If cache can't be acquired immediately,
   * then computation is scheduled in the background and null is returned.
   */
  fun <T> tryGet(key: Any, computable: SyncCacheComputable<T?>): T? {
    val result = section.tryUse {
      cache.containsKey(key) to cache[key]
    }

    if (result is Acquired<Pair<Boolean, Any?>> && result.value.first) {
      @Suppress("UNCHECKED_CAST")
      return result.value.second as T?
    } else {
      // in case there's a computation running for the same key, then this
      // is no-op, otherwise this is
      scope.launch {
        get(key, computable)
      }
    }

    return null
  }

  /** Computes a value derived from the sync project data and caches it until the next sync.  */
  fun <T> get(key: Any, computable: SyncCacheComputable<T?>): T? {
    return section.withLockInterruptible {
      if (cache.containsKey(key)) {
        @Suppress("UNCHECKED_CAST")
        return@withLockInterruptible cache[key] as T?
      }
      val blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData()
          ?: return@withLockInterruptible null

      val value = computable.compute(project, blazeProjectData)
      cache[key] = value
      value
    }
  }

  @VisibleForTesting
  fun clear() {
    // use of raw runBlocking here because we're called by
    // this plugin and thread context might not be correctly set
    // as we haven't addressed this yet
    runBlocking {
      section.withLock {
        cache.clear()
      }
    }
  }

  internal class ClearSyncCache : SyncListener {
    override fun onSyncComplete(
      project: Project,
      context: BlazeContext?,
      importSettings: BlazeImportSettings?,
      projectViewSet: ProjectViewSet?,
      buildIds: ImmutableSet<Int>?,
      blazeProjectData: BlazeProjectData?,
      syncMode: SyncMode?,
      syncResult: SyncResult?
    ) {
      val syncCache = getInstance(project)
      syncCache.clear()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SyncCache {
      return project.getService(SyncCache::class.java)
    }
  }
}
