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
package com.google.idea.blaze.java.sync.source;

import com.google.common.base.*;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.executor.TransientExecutor;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.projectview.SourceTestConfig;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.util.PackagePrefixCalculator;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.intellij.openapi.diagnostic.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * This is a utility class for calculating the java sources and their package prefixes given
 * a module and its Blaze {@link ArtifactLocation} list.
 */
public final class SourceDirectoryCalculator {

  private static final Logger LOG = Logger.getInstance(SourceDirectoryCalculator.class);

  private static final Splitter PACKAGE_SPLITTER = Splitter.on('.');
  private static final Splitter PATH_SPLITTER = Splitter.on('/');
  private static final Joiner PACKAGE_JOINER = Joiner.on('.');
  private static final Joiner PATH_JOINER = Joiner.on('/');

  private static final Comparator<WorkspacePath> WORKSPACE_PATH_COMPARATOR =
    (o1, o2) -> o1.relativePath().compareTo(o2.relativePath());

  private static final JavaPackageReader generatedFileJavaPackageReader = new FilePathJavaPackageReader();
  private final ListeningExecutorService executorService = MoreExecutors.sameThreadExecutor();
  private final ListeningExecutorService packageReaderExecutorService = MoreExecutors.listeningDecorator(new TransientExecutor(16));

  public ImmutableList<BlazeContentEntry> calculateContentEntries(
    BlazeContext context,
    WorkspaceRoot workspaceRoot,
    SourceTestConfig sourceTestConfig,
    ArtifactLocationDecoder artifactLocationDecoder,
    Collection<WorkspacePath> rootDirectories,
    Collection<SourceArtifact> sources,
    Map<Label, ArtifactLocation> javaPackageManifests) {

    ManifestFilePackageReader manifestFilePackageReader = Scope.push(context, (childContext) -> {
      childContext.push(new TimingScope("ReadPackageManifests"));
      Map<Label, Map<String, String>> manifestMap = PackageManifestReader.getInstance().readPackageManifestFiles(
        childContext,
        artifactLocationDecoder,
        javaPackageManifests,
        packageReaderExecutorService
      );
      return new ManifestFilePackageReader(manifestMap);
    });

    final List<JavaPackageReader> javaPackageReaders = Lists.newArrayList(
      manifestFilePackageReader,
      JavaSourcePackageReader.getInstance(),
      generatedFileJavaPackageReader);

    Collection<SourceArtifact> nonGeneratedSources = filterGeneratedArtifacts(sources);

    // Sort artifacts and excludes into their respective workspace paths
    Multimap<WorkspacePath, SourceArtifact> sourcesUnderDirectoryRoot =
      sortArtifactLocationsByRootDirectory(context, rootDirectories, nonGeneratedSources);

    List<BlazeContentEntry> result = Lists.newArrayList();
    for (WorkspacePath workspacePath : rootDirectories) {
      File contentRoot = workspaceRoot.fileForPath(workspacePath);
      ImmutableList<BlazeSourceDirectory> sourceDirectories = calculateSourceDirectoriesForContentRoot(
        context,
        sourceTestConfig,
        workspaceRoot,
        workspacePath,
        sourcesUnderDirectoryRoot.get(workspacePath),
        javaPackageReaders
      );
      if (!sourceDirectories.isEmpty()) {
        result.add(new BlazeContentEntry(contentRoot, sourceDirectories));
      }
    }
    Collections.sort(result, (lhs, rhs) -> lhs.contentRoot.compareTo(rhs.contentRoot));
    return ImmutableList.copyOf(result);
  }

  private Collection<SourceArtifact> filterGeneratedArtifacts(Collection<SourceArtifact> artifactLocations) {
    return artifactLocations
      .stream()
      .filter(sourceArtifact -> sourceArtifact.artifactLocation.isSource())
      .collect(Collectors.toList());
  }

  private static Multimap<WorkspacePath, SourceArtifact> sortArtifactLocationsByRootDirectory(
    BlazeContext context,
    Collection<WorkspacePath> rootDirectories,
    Collection<SourceArtifact> sources) {

    Multimap<WorkspacePath, SourceArtifact> result = ArrayListMultimap.create();

    for (SourceArtifact sourceArtifact : sources) {
      WorkspacePath foundWorkspacePath = rootDirectories
        .stream()
        .filter(rootDirectory -> isUnderRootDirectory(rootDirectory, sourceArtifact.artifactLocation.getRelativePath()))
        .findFirst()
        .orElse(null);

      if (foundWorkspacePath != null) {
        result.put(foundWorkspacePath, sourceArtifact);
      }
      else if (sourceArtifact.artifactLocation.isSource()) {
        File sourceFile = sourceArtifact.artifactLocation.getFile();
        String message = String.format(
          "Did not add %s. You're probably using a java file from outside the workspace"
          + "that has been exported using export_files. Don't do that.", sourceFile);
        IssueOutput
          .warn(message)
          .inFile(sourceFile)
          .submit(context);
      }
    }
    return result;
  }

  private static boolean isUnderRootDirectory(WorkspacePath rootDirectory, String relativePath) {
    if (rootDirectory.isWorkspaceRoot()) {
      return true;
    }
    String rootDirectoryString = rootDirectory.toString();
    return relativePath.startsWith(rootDirectoryString)
      && (relativePath.length() == rootDirectoryString.length()
          || (relativePath.charAt(rootDirectoryString.length()) == '/'));
  }

  /**
   * Calculates all source directories for a single content root.
   */
  private ImmutableList<BlazeSourceDirectory> calculateSourceDirectoriesForContentRoot(
    BlazeContext context,
    SourceTestConfig sourceTestConfig,
    WorkspaceRoot workspaceRoot,
    WorkspacePath directoryRoot,
    Collection<SourceArtifact> sourceArtifacts,
    Collection<JavaPackageReader> javaPackageReaders) {

    // Split out java files
    List<SourceArtifact> javaArtifacts = Lists.newArrayList();
    for (SourceArtifact sourceArtifact : sourceArtifacts) {
      if (isJavaFile(sourceArtifact.artifactLocation)) {
        javaArtifacts.add(sourceArtifact);
      }
    }

    List<BlazeSourceDirectory> result = Lists.newArrayList();

    // Add java source directories
    calculateJavaSourceDirectories(
      context,
      workspaceRoot,
      directoryRoot,
      sourceTestConfig,
      javaArtifacts,
      javaPackageReaders,
      result
    );

    Collections.sort(result, BlazeSourceDirectory.COMPARATOR);
    return ImmutableList.copyOf(result);
  }

  /**
   * Adds the java source directories.
   */
  private void calculateJavaSourceDirectories(
    BlazeContext context,
    WorkspaceRoot workspaceRoot,
    WorkspacePath directoryRoot,
    SourceTestConfig sourceTestConfig,
    Collection<SourceArtifact> javaArtifacts,
    Collection<JavaPackageReader> javaPackageReaders,
    Collection<BlazeSourceDirectory> result) {

    List<SourceRoot> sourceRootsPerFile = Lists.newArrayList();

    // Get java sources
    List<ListenableFuture<SourceRoot>> sourceRootFutures = Lists.newArrayList();
    for (final SourceArtifact sourceArtifact : javaArtifacts) {
      ListenableFuture<SourceRoot> future = executorService.submit(() -> sourceRootForJavaSource(
        context,
        sourceArtifact,
        javaPackageReaders
      ));
      sourceRootFutures.add(future);
    }
    try {
      for (SourceRoot sourceRoot : Futures.allAsList(sourceRootFutures).get()) {
        if (sourceRoot != null) {
          sourceRootsPerFile.add(sourceRoot);
        }
      }
    }
    catch (ExecutionException | InterruptedException e) {
      LOG.error(e);
      throw new IllegalStateException("Could not read sources");
    }

    // Sort source roots into their respective directories
    Multimap<WorkspacePath, SourceRoot> sourceDirectoryToSourceRoots = HashMultimap.create();
    for (SourceRoot sourceRoot : sourceRootsPerFile) {
      sourceDirectoryToSourceRoots.put(sourceRoot.workspacePath, sourceRoot);
    }

    // Create a mapping from directory to package prefix
    Map<WorkspacePath, SourceRoot> workspacePathToCandidateRoot = Maps.newHashMap();
    for (WorkspacePath workspacePath : sourceDirectoryToSourceRoots.keySet()) {
      Collection<SourceRoot> sources = sourceDirectoryToSourceRoots.get(workspacePath);
      Multiset<String> packages = HashMultiset.create();

      for (SourceRoot source : sources) {
        packages.add(source.packagePrefix);
      }

      final String directoryPackagePrefix;
      // Common case -- all source files agree on a single package
      if (packages.elementSet().size() == 1) {
        directoryPackagePrefix = packages.elementSet().iterator().next();
      }
      else {
        directoryPackagePrefix = pickMostFrequentlyOccurring(packages);
      }

      // These properties must be the same for all files in the directory
      SourceRoot sourceFile = sources.iterator().next();

      SourceRoot candidateRoot = new SourceRoot(workspacePath, directoryPackagePrefix);
      workspacePathToCandidateRoot.put(workspacePath, candidateRoot);
    }

    // Add content entry base if it doesn't exist
    if (!workspacePathToCandidateRoot.containsKey(directoryRoot)) {
      SourceRoot candidateRoot = new SourceRoot(directoryRoot, PackagePrefixCalculator.packagePrefixOf(directoryRoot));
      workspacePathToCandidateRoot.put(directoryRoot, candidateRoot);
    }

    // Merge source roots
    // We have to do this in directory order to ensure we encounter roots before
    // their subdirectories
    Map<WorkspacePath, SourceRoot> mergedSourceRoots = Maps.newHashMap();
    List<WorkspacePath> sortedWorkspacePaths = Lists.newArrayList(workspacePathToCandidateRoot.keySet());
    Collections.sort(sortedWorkspacePaths, WORKSPACE_PATH_COMPARATOR);
    for (WorkspacePath workspacePath : sortedWorkspacePaths) {
      SourceRoot candidateRoot = workspacePathToCandidateRoot.get(workspacePath);
      SourceRoot bestNewRoot = candidateRoot;
      for (SourceRoot mergedSourceRoot : new CandidateRoots(directoryRoot, candidateRoot)) {
        SourceRoot existingSourceRoot = mergedSourceRoots.get(mergedSourceRoot.workspacePath);
        if (existingSourceRoot != null) {
          if (existingSourceRoot.packagePrefix.equals(mergedSourceRoot.packagePrefix)) {
            // Do not create new source root -- merge into preexisting source root
            // Since we already decided to establish one here, there is also
            // no need to go further up the tree
            bestNewRoot = null;
          }
          break;
        }
        bestNewRoot = mergedSourceRoot;
      }

      if (bestNewRoot != null) {
        mergedSourceRoots.put(bestNewRoot.workspacePath, bestNewRoot);
      }
    }

    // Add merged source roots
    for (SourceRoot sourceRoot : mergedSourceRoots.values()) {
      result.add(BlazeSourceDirectory.builder(workspaceRoot.fileForPath(sourceRoot.workspacePath))
                              .setPackagePrefix(sourceRoot.packagePrefix)
                              .setTest(sourceTestConfig.isTestSource(sourceRoot.workspacePath.relativePath()))
                              .setGenerated(false)
                              .build());
    }
  }

  private static <T> T pickMostFrequentlyOccurring(Multiset<T> set) {
    Preconditions.checkArgument(set.size() > 0);

    T best = null;
    int bestCount = 0;

    for (T candidate : set.elementSet()) {
      int candidateCount = set.count(candidate);
      if (candidateCount > bestCount) {
        best = candidate;
        bestCount = candidateCount;
      }
    }
    return best;
  }

  @Nullable
  private static SourceRoot sourceRootForJavaSource(
    BlazeContext context,
    SourceArtifact sourceArtifact,
    Collection<JavaPackageReader> javaPackageReaders) {

    File javaFile = sourceArtifact.artifactLocation.getFile();

    String declaredPackage = null;
    for (JavaPackageReader reader : javaPackageReaders) {
      declaredPackage = reader.getDeclaredPackageOfJavaFile(context, sourceArtifact);
      if (declaredPackage != null) {
        break;
      }
    }
    if (declaredPackage == null) {
      IssueOutput
        .warn("Failed to inspect the package name of java source file: " + javaFile)
        .inFile(javaFile)
        .submit(context);
      return null;
    }
    return new SourceRoot(
      new WorkspacePath(new File(sourceArtifact.artifactLocation.getRelativePath()).getParent()),
      declaredPackage
    );
  }

  static class SourceRoot {
    final WorkspacePath workspacePath;
    final String packagePrefix;
    public SourceRoot(WorkspacePath workspacePath, String packagePrefix) {
      this.workspacePath = workspacePath;
      this.packagePrefix = packagePrefix;
    }
    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SourceRoot that = (SourceRoot)o;
      return Objects.equal(workspacePath, that.workspacePath)
             && Objects.equal(packagePrefix, that.packagePrefix);
    }
    @Override
    public int hashCode() {
      return Objects.hashCode(workspacePath, packagePrefix);
    }

    @Override
    public String toString() {
      return "SourceRoot {" + '\n'
             + "  workspacePath: " + workspacePath + '\n'
             + "  packagePrefix: " + packagePrefix + '\n'
             + '}';
    }
  }

  private static boolean isJavaFile(ArtifactLocation artifactLocation) {
    return artifactLocation.getRelativePath().endsWith(".java");
  }

  static class CandidateRoots implements Iterable<SourceRoot> {
    private static final List<String> EMPTY_LIST = ImmutableList.of();

    private final SourceRoot candidateRoot;
    private final WorkspacePath directoryRoot;

    CandidateRoots(
      WorkspacePath directoryRoot,
      SourceRoot candidateRoot) {
      this.directoryRoot = directoryRoot;
      this.candidateRoot = candidateRoot;
    }

    @Override
    public Iterator<SourceRoot> iterator() {
      return new CandidateRootIterator();
    }

    class CandidateRootIterator implements Iterator<SourceRoot> {
      private final List<String> packageComponents;
      private final List<String> pathComponents;
      private int packageIndex;
      private int pathIndex;

      CandidateRootIterator() {
        int directoryRootLength = directoryRoot.relativePath().length();
        String relativePath = candidateRoot.workspacePath.relativePath();
        final String sourcePathRelativeToModule;
        if (relativePath.length() > directoryRootLength) {
          if (directoryRootLength > 0) {
            sourcePathRelativeToModule = relativePath.substring(directoryRootLength + 1);
          } else {
            sourcePathRelativeToModule = relativePath;
          }
        } else {
          sourcePathRelativeToModule = "";
        }

        this.packageComponents = PACKAGE_SPLITTER.splitToList(candidateRoot.packagePrefix);
        this.pathComponents = !Strings.isNullOrEmpty(sourcePathRelativeToModule)
                              ? PATH_SPLITTER.splitToList(sourcePathRelativeToModule) : EMPTY_LIST;
        this.packageIndex = packageComponents.size() - 1;
        this.pathIndex = pathComponents.size() - 1;
      }

      @Override
      public boolean hasNext() {
        return (packageIndex >= 0 && pathIndex >= 0 && packageComponents.get(packageIndex).equals(pathComponents.get(pathIndex)));
      }

      @Override
      public SourceRoot next() {
        String directoryRootRelativePath = PATH_JOINER.join(pathComponents.subList(0, pathIndex));
        final WorkspacePath workspacePath;
        if (directoryRootRelativePath.isEmpty()){
          workspacePath = directoryRoot;
        } else if (directoryRoot.isWorkspaceRoot()) {
          workspacePath = new WorkspacePath(directoryRootRelativePath);
        } else {
          workspacePath = new WorkspacePath(PATH_JOINER.join(directoryRoot.relativePath(), directoryRootRelativePath));
        }

        SourceRoot sourceRoot = new SourceRoot(
          workspacePath,
          PACKAGE_JOINER.join(packageComponents.subList(0, packageIndex))
        );
        --packageIndex;
        --pathIndex;
        return sourceRoot;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    }
  }
}
