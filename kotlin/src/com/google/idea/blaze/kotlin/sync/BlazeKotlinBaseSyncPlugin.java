package com.google.idea.blaze.kotlin.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.kotlin.BlazeKotlin;

import java.util.Collection;
import java.util.Set;

abstract class BlazeKotlinBaseSyncPlugin implements BlazeSyncPlugin {
    @Override
    public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
        return Blaze.getBuildSystem(null) == Blaze.BuildSystem.Bazel
                ? ImmutableSet.of(LanguageClass.KOTLIN)
                : ImmutableSet.of();
    }

    @Override
    public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
        return languages.contains(LanguageClass.KOTLIN)
                ? ImmutableList.of(BlazeKotlin.PLUGIN_ID)
                : ImmutableList.of();
    }
}
