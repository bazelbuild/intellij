/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BlazeKotlinWorkspaceImporter {
    private final Project project;
    private final BlazeContext context;
    private final WorkspaceRoot workspaceRoot;
    private final ProjectViewSet projectViewSet;
    private final TargetMap targetMap;

    public BlazeKotlinWorkspaceImporter(
            Project project,
            BlazeContext context,
            WorkspaceRoot workspaceRoot,
            ProjectViewSet projectViewSet,
            TargetMap targetMap) {
        this.project = project;
        this.context = context;
        this.workspaceRoot = workspaceRoot;
        this.projectViewSet = projectViewSet;
        this.targetMap = targetMap;
    }

    /**
     * This predicate matches `java_imports` which are created by the kotlin_rules for the `kotlin_library` rule. This is an interim solution so that at least
     * the `kotlin_library` rule targets sync their dependencies into the workspace.
     *
     * see: https://github.com/pubref/rules_kotlin/blob/master/kotlin/rules.bzl
     *
     * In order to get skylark support and to cover the `kotlin_binary`, `kotlin_test` the rules should be updated.
     * see: https://github.com/pubref/rules_kotlin/issues/44
     */
    private static boolean isKotlinTarget(TargetIdeInfo ideInfo) {
        boolean hasKotlinRuntimeDependency = false;
        if(!ideInfo.kindIsOneOf(Kind.JAVA_IMPORT) && !ideInfo.key.label.isExternal()) {
            return false;
        } else {
            for (Dependency dependency : ideInfo.dependencies) {
                Label label = dependency.targetKey.label;
                String workspaceName = label.externalWorkspaceName();
                if(workspaceName != null && workspaceName.equals("com_github_jetbrains_kotlin") && label.targetName().toString().equals("runtime")) {
                    hasKotlinRuntimeDependency = true;
                    break;
                }
            }
            return hasKotlinRuntimeDependency;
        }
    }

    public BlazeKotlinImportResult importWorkspace() {
        ProjectViewTargetImportFilter importFilter = new ProjectViewTargetImportFilter(project, workspaceRoot, projectViewSet);
        List<TargetKey> kotlinSourceTargets =
                targetMap
                        .targets()
                        .stream()
                        .filter(target -> target.javaIdeInfo != null)
                        .filter(BlazeKotlinWorkspaceImporter::isKotlinTarget)
                        .filter(importFilter::isSourceTarget)
                        .map(target -> target.key)
                        .collect(Collectors.toList());
        Map<LibraryKey, BlazeJarLibrary> libraries = Maps.newHashMap();

        // Add every jar in the transitive closure of dependencies.
        // Direct dependencies of the working set will be double counted by BlazeJavaWorkspaceImporter,
        // but since they'll all merged into one set, we will end up with exactly one of each.
        for (TargetKey dependency : TransitiveDependencyMap.getTransitiveDependencies(kotlinSourceTargets, targetMap)) {
            TargetIdeInfo target = targetMap.get(dependency);
            if (target == null) {
                continue;
            }
            // Except source targets.
            if (importFilter.isSourceTarget(target) && JavaSourceFilter.canImportAsSource(target)) {
                continue;
            }
            if (target.javaIdeInfo != null) {
                target
                        .javaIdeInfo
                        .jars.stream()
                        .map(BlazeJarLibrary::new)
                        .forEach(library -> libraries.putIfAbsent(library.key, library));
            }
        }
        return new BlazeKotlinImportResult(ImmutableMap.copyOf(libraries));
    }
}
