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

package com.google.idea.blaze.base.sync.aspects;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.*;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import com.google.repackaged.protobuf.ProtocolStringList;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Conversion functions from new aspect-style Bazel IDE info to ASWB internal classes.
 */
public class IdeInfoFromProtobuf {

  @Nullable
  public static RuleIdeInfo makeRuleIdeInfo(WorkspaceLanguageSettings workspaceLanguageSettings,
                                            ArtifactLocationDecoder decoder,
                                            AndroidStudioIdeInfo.RuleIdeInfo message) {
    Kind kind = getKind(message);
    if (kind == null) {
      return null;
    }
    if (!workspaceLanguageSettings.isLanguageActive(kind.getLanguageClass())) {
      return null;
    }

    Label label = new Label(message.getLabel());
    ArtifactLocation buildFile = getBuildFile(decoder, message);

    Collection<Label> dependencies = makeLabelListFromProtobuf(message.getDependenciesList());
    Collection<Label> runtimeDeps = makeLabelListFromProtobuf(message.getRuntimeDepsList());
    Collection<String> tags = ImmutableList.copyOf(message.getTagsList());

    Collection<ArtifactLocation> sources = Lists.newArrayList();
    CRuleIdeInfo cRuleIdeInfo = null;
    if (message.hasCRuleIdeInfo()) {
      cRuleIdeInfo = makeCRuleIdeInfo(decoder, message.getCRuleIdeInfo());
      sources.addAll(cRuleIdeInfo.sources);
    }
    CToolchainIdeInfo cToolchainIdeInfo = null;
    if (message.hasCToolchainIdeInfo()) {
      cToolchainIdeInfo = makeCToolchainIdeInfo(message.getCToolchainIdeInfo());
    }
    JavaRuleIdeInfo javaRuleIdeInfo = null;
    if (message.hasJavaRuleIdeInfo()) {
      javaRuleIdeInfo = makeJavaRuleIdeInfo(decoder, message.getJavaRuleIdeInfo());
      Collection<ArtifactLocation> javaSources = makeArtifactLocationList(decoder, message.getJavaRuleIdeInfo().getSourcesList());
      sources.addAll(javaSources);
    }
    AndroidRuleIdeInfo androidRuleIdeInfo = null;
    if (message.hasAndroidRuleIdeInfo()) {
      androidRuleIdeInfo = makeAndroidRuleIdeInfo(decoder, message.getAndroidRuleIdeInfo());
    }
    TestIdeInfo testIdeInfo = null;
    if (message.hasTestInfo()) {
      testIdeInfo = makeTestIdeInfo(message.getTestInfo());
    }
    ProtoLibraryLegacyInfo protoLibraryLegacyInfo = null;
    if (message.hasProtoLibraryLegacyJavaIdeInfo()) {
      protoLibraryLegacyInfo = makeProtoLibraryLegacyInfo(decoder, message.getProtoLibraryLegacyJavaIdeInfo());
    }
    JavaToolchainIdeInfo javaToolchainIdeInfo = null;
    if (message.hasJavaToolchainIdeInfo()) {
      javaToolchainIdeInfo = makeJavaToolchainIdeInfo(message.getJavaToolchainIdeInfo());
    }

    return new RuleIdeInfo(
      label,
      kind,
      buildFile,
      dependencies,
      runtimeDeps,
      tags,
      sources,
      cRuleIdeInfo,
      cToolchainIdeInfo,
      javaRuleIdeInfo,
      androidRuleIdeInfo,
      testIdeInfo,
      protoLibraryLegacyInfo,
      javaToolchainIdeInfo
    );
  }

  @Nullable
  private static ArtifactLocation getBuildFile(ArtifactLocationDecoder decoder,
                                               AndroidStudioIdeInfo.RuleIdeInfo message) {
    if (message.hasBuildFileArtifactLocation()) {
      return makeArtifactLocation(decoder, message.getBuildFileArtifactLocation());
    }
    return null;
  }

  private static CRuleIdeInfo makeCRuleIdeInfo(
    ArtifactLocationDecoder decoder,
    AndroidStudioIdeInfo.CRuleIdeInfo cRuleIdeInfo
  ) {
    List<ArtifactLocation> sources = makeArtifactLocationList(decoder, cRuleIdeInfo.getSourceList());
    List<ExecutionRootPath> transitiveIncludeDirectories = makeExecutionRootPathList(cRuleIdeInfo.getTransitiveIncludeDirectoryList());
    List<ExecutionRootPath> transitiveQuoteIncludeDirectories =
      makeExecutionRootPathList(cRuleIdeInfo.getTransitiveQuoteIncludeDirectoryList());
    List<ExecutionRootPath> transitiveSystemIncludeDirectories =
      makeExecutionRootPathList(cRuleIdeInfo.getTransitiveSystemIncludeDirectoryList());

    CRuleIdeInfo.Builder builder = CRuleIdeInfo.builder()
      .addSources(sources)
      .addTransitiveIncludeDirectories(transitiveIncludeDirectories)
      .addTransitiveQuoteIncludeDirectories(transitiveQuoteIncludeDirectories)
      .addTransitiveDefines(cRuleIdeInfo.getTransitiveDefineList())
      .addTransitiveSystemIncludeDirectories(transitiveSystemIncludeDirectories)
    ;

    return builder.build();
  }

  private static List<ExecutionRootPath> makeExecutionRootPathList(Iterable<String> relativePaths) {
    List<ExecutionRootPath> workspacePaths = Lists.newArrayList();
    for (String relativePath : relativePaths) {
      workspacePaths.add(new ExecutionRootPath(relativePath));
    }
    return workspacePaths;
  }

  private static CToolchainIdeInfo makeCToolchainIdeInfo(AndroidStudioIdeInfo.CToolchainIdeInfo cToolchainIdeInfo) {
    Collection<ExecutionRootPath> builtInIncludeDirectories = makeExecutionRootPathList(cToolchainIdeInfo.getBuiltInIncludeDirectoryList());
    ExecutionRootPath cppExecutable = new ExecutionRootPath(cToolchainIdeInfo.getCppExecutable());
    ExecutionRootPath preprocessorExecutable = new ExecutionRootPath(cToolchainIdeInfo.getPreprocessorExecutable());

    UnfilteredCompilerOptions unfilteredCompilerOptions =
      new UnfilteredCompilerOptions(cToolchainIdeInfo.getUnfilteredCompilerOptionList());

    CToolchainIdeInfo.Builder builder = CToolchainIdeInfo.builder()
      .addBaseCompilerOptions(cToolchainIdeInfo.getBaseCompilerOptionList())
      .addCCompilerOptions(cToolchainIdeInfo.getCOptionList())
      .addCppCompilerOptions(cToolchainIdeInfo.getCppOptionList())
      .addLinkOptions(cToolchainIdeInfo.getLinkOptionList())
      .addBuiltInIncludeDirectories(builtInIncludeDirectories)
      .setCppExecutable(cppExecutable)
      .setPreprocessorExecutable(preprocessorExecutable)
      .setTargetName(cToolchainIdeInfo.getTargetName())
      .addUnfilteredCompilerOptions(unfilteredCompilerOptions.getToolchainFlags())
      .addUnfilteredToolchainSystemIncludes(unfilteredCompilerOptions.getToolchainSysIncludes())
      ;

    return builder.build();
  }

  private static JavaRuleIdeInfo makeJavaRuleIdeInfo(ArtifactLocationDecoder decoder,
                                                     AndroidStudioIdeInfo.JavaRuleIdeInfo javaRuleIdeInfo) {
    return new JavaRuleIdeInfo(
      makeLibraryArtifactList(decoder, javaRuleIdeInfo.getJarsList()),
      makeLibraryArtifactList(decoder, javaRuleIdeInfo.getGeneratedJarsList()),
      javaRuleIdeInfo.hasPackageManifest() ? makeArtifactLocation(decoder, javaRuleIdeInfo.getPackageManifest()) : null,
      javaRuleIdeInfo.hasJdeps() ? makeArtifactLocation(decoder, javaRuleIdeInfo.getJdeps()) : null
    );
  }

  private static AndroidRuleIdeInfo makeAndroidRuleIdeInfo(ArtifactLocationDecoder decoder,
                                                           AndroidStudioIdeInfo.AndroidRuleIdeInfo androidRuleIdeInfo) {
    return new AndroidRuleIdeInfo(
      makeArtifactLocationList(decoder, androidRuleIdeInfo.getResourcesList()),
      androidRuleIdeInfo.getJavaPackage(),
      androidRuleIdeInfo.getGenerateResourceClass(),
      androidRuleIdeInfo.hasManifest() ? makeArtifactLocation(decoder, androidRuleIdeInfo.getManifest()) : null,
      androidRuleIdeInfo.hasIdlJar() ? makeLibraryArtifact(decoder, androidRuleIdeInfo.getIdlJar()) : null,
      androidRuleIdeInfo.hasResourceJar() ? makeLibraryArtifact(decoder, androidRuleIdeInfo.getResourceJar()) : null,
      androidRuleIdeInfo.getHasIdlSources(),
      !Strings.isNullOrEmpty(androidRuleIdeInfo.getLegacyResources()) ? new Label(androidRuleIdeInfo.getLegacyResources()) : null
    );
  }

  private static TestIdeInfo makeTestIdeInfo(AndroidStudioIdeInfo.TestInfo testInfo) {
    String size = testInfo.getSize();
    TestIdeInfo.TestSize testSize = TestIdeInfo.DEFAULT_RULE_TEST_SIZE;
    if (!Strings.isNullOrEmpty(size)) {
      switch (size) {
        case "small":
          testSize = TestIdeInfo.TestSize.SMALL;
          break;
        case "medium":
          testSize = TestIdeInfo.TestSize.MEDIUM;
          break;
        case "large":
          testSize = TestIdeInfo.TestSize.LARGE;
          break;
        case "enormous":
          testSize = TestIdeInfo.TestSize.ENORMOUS;
          break;
        default:
          break;
      }
    }
    return new TestIdeInfo(testSize);
  }

  private static ProtoLibraryLegacyInfo makeProtoLibraryLegacyInfo(ArtifactLocationDecoder decoder,
                                                                   AndroidStudioIdeInfo.ProtoLibraryLegacyJavaIdeInfo protoLibraryLegacyJavaIdeInfo) {
    final ProtoLibraryLegacyInfo.ApiFlavor apiFlavor;
    if (protoLibraryLegacyJavaIdeInfo.getApiVersion() == 1) {
      apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1;
    } else {
      switch (protoLibraryLegacyJavaIdeInfo.getApiFlavor()) {
        case MUTABLE:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.MUTABLE;
          break;
        case IMMUTABLE:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE;
          break;
        case BOTH:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.BOTH;
          break;
        default:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.NONE;
          break;
      }
    }
    return new ProtoLibraryLegacyInfo(
      apiFlavor,
      makeLibraryArtifactList(decoder, protoLibraryLegacyJavaIdeInfo.getJars1List()),
      makeLibraryArtifactList(decoder, protoLibraryLegacyJavaIdeInfo.getJarsMutableList()),
      makeLibraryArtifactList(decoder, protoLibraryLegacyJavaIdeInfo.getJarsImmutableList())
    );
  }

  private static JavaToolchainIdeInfo makeJavaToolchainIdeInfo(AndroidStudioIdeInfo.JavaToolchainIdeInfo javaToolchainIdeInfo) {
    return new JavaToolchainIdeInfo(javaToolchainIdeInfo.getSourceVersion(), javaToolchainIdeInfo.getTargetVersion());
  }

  private static Collection<LibraryArtifact> makeLibraryArtifactList(
    ArtifactLocationDecoder decoder,
    List<AndroidStudioIdeInfo.LibraryArtifact> jarsList) {
    ImmutableList.Builder<LibraryArtifact> builder = ImmutableList.builder();
    for (AndroidStudioIdeInfo.LibraryArtifact libraryArtifact : jarsList) {
      LibraryArtifact lib = makeLibraryArtifact(decoder, libraryArtifact);
      if (lib != null) {
        builder.add(lib);
      }
    }
    return builder.build();
  }

  @Nullable
  private static LibraryArtifact makeLibraryArtifact(ArtifactLocationDecoder decoder,
                                                     AndroidStudioIdeInfo.LibraryArtifact libraryArtifact) {
    ArtifactLocation runtimeJar = libraryArtifact.hasJar()
                                  ? makeArtifactLocation(decoder, libraryArtifact.getJar()) : null;
    ArtifactLocation iJar = libraryArtifact.hasInterfaceJar()
                            ? makeArtifactLocation(decoder, libraryArtifact.getInterfaceJar()) : runtimeJar;
    ArtifactLocation sourceJar = libraryArtifact.hasSourceJar()
                                 ? makeArtifactLocation(decoder, libraryArtifact.getSourceJar()) : null;
    if (iJar == null) {
      // Failed to find ArtifactLocation file -- presumably because it was removed from file system since blaze build
      return null;
    }
    return new LibraryArtifact(
      iJar,
      runtimeJar,
      sourceJar
    );
  }

  private static List<ArtifactLocation> makeArtifactLocationList(
    ArtifactLocationDecoder decoder,
    List<AndroidStudioIdeInfo.ArtifactLocation> sourcesList) {
    ImmutableList.Builder<ArtifactLocation> builder = ImmutableList.builder();
    for (AndroidStudioIdeInfo.ArtifactLocation pbArtifactLocation : sourcesList) {
      ArtifactLocation loc = makeArtifactLocation(decoder, pbArtifactLocation);
      if (loc != null) {
        builder.add(loc);
      }
    }
    return builder.build();
  }

  @Nullable
  private static ArtifactLocation makeArtifactLocation(ArtifactLocationDecoder decoder,
                                                       AndroidStudioIdeInfo.ArtifactLocation pbArtifactLocation) {
    if (pbArtifactLocation == null) {
      return null;
    }
    return decoder.decode(pbArtifactLocation);
  }

  private static Collection<Label> makeLabelListFromProtobuf(ProtocolStringList dependenciesList) {
    ImmutableList.Builder<Label> dependenciesBuilder = ImmutableList.builder();
    for (String dependencyLabel : dependenciesList) {
      dependenciesBuilder.add(new Label(dependencyLabel));
    }
    return dependenciesBuilder.build();
  }

  @Nullable
  private static Kind getKind(AndroidStudioIdeInfo.RuleIdeInfo rule) {
    String kindString = rule.getKindString();
    if (!Strings.isNullOrEmpty(kindString)) {
      return Kind.fromString(kindString);
    }
    return null;
  }
}
