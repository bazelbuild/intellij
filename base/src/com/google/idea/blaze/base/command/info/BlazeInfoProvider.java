/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.info;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * <p> Providing a {@link com.google.idea.blaze.base.command.info.BlazeInfo}, the result will be cached and invalidated on any sync failure. </p>
 *
 * @see BlazeInfoRunner
 */
public class BlazeInfoProvider {
    private static final Logger logger = Logger.getInstance(BlazeInfoProvider.class);
    private static final BoolExperiment enabled =
            new BoolExperiment("blaze.info.provider.enabled", false);

    private final Project project;
    private volatile BlazeInfo blazeInfo;

    public BlazeInfoProvider(Project project) {
        this.project = project;
    }

    public static BlazeInfoProvider getInstance(Project project) {
        return project.getService(BlazeInfoProvider.class);
    }

    public static Boolean isEnabled() {
        return enabled.getValue();
    }

    public ListenableFuture<BlazeInfo> getBlazeInfo(
            BlazeContext context,
            List<String> blazeFlags) {
        return BlazeExecutor.getInstance().submit(() -> {
            BlazeInfo info = getCachedBlazeInfo(context,
                    blazeFlags);
            if (info == null) {
                throw new BlazeInfoException("Unable to get blaze info");
            }
            return info;
        });
    }

    private @Nullable BlazeInfo getCachedBlazeInfo(
            BlazeContext context,
            List<String> blazeFlags) {
        if (blazeInfo != null) {
            logger.debug("Returning cached BlazeInfo");
            return blazeInfo;
        }
        try {
            BlazeImportSettings importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
            if (importSettings == null) {
                return null;
            }
            BuildSystem.BuildInvoker buildInvoker = Blaze.getBuildSystemProvider(project)
                    .getBuildSystem()
                    .getDefaultInvoker(project, context);
            logger.debug("Running bazel info");
            blazeInfo = BlazeInfoRunner.getInstance().runBlazeInfo(
                    project,
                    buildInvoker,
                    BlazeContext.create(),
                    importSettings.getBuildSystem(),
                    blazeFlags
            ).get();
            return blazeInfo;
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Unable to run blaze info", e);
            return null;
        }
    }

    public void invalidate() {
        logger.debug("invalidating");
        blazeInfo = null;
    }

    public static final class Invalidator implements SyncListener {
        @Override
        public void afterSync(
                Project project,
                BlazeContext context,
                SyncMode syncMode,
                SyncResult syncResult,
                ImmutableSet<Integer> buildIds) {
            if(!isEnabled()) {
                return;
            }
            BlazeInfoProvider provider = BlazeInfoProvider.getInstance(project);
            if(provider != null) {
                provider.invalidate();
            }
        }
    }
}
