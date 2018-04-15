package com.google.idea.blaze.scala.run.producers;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.intellij.openapi.project.Project;

public class ScalaLibraryRunConfigurationProducer
  extends BlazeScalaMainClassRunConfigurationProducer {
  private static final String SCALA_BINARY_FOR_LIBS_MAP_KEY = "BlazeScalaBinaryForLibsMap";

  protected ScalaLibraryRunConfigurationProducer() {
    super(SCALA_BINARY_FOR_LIBS_MAP_KEY);
  }

  @Override
  protected FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
      project,
      projectData.artifactLocationDecoder,
      toBinaryTargetMap(projectData.targetMap),
      (target) -> target.kind == Kind.SCALA_BINARY && target.isPlainTarget());
  }

  private TargetMap toBinaryTargetMap(TargetMap targetMap) {
    ImmutableMap<TargetKey, TargetIdeInfo> binaryTargets =
      targetMap.targets()
        .stream()
        .filter(t -> t.kind == Kind.SCALA_LIBRARY)
        .collect(ImmutableMap.toImmutableMap(
          k -> TargetKey.forPlainTarget(Label.create("//:main")),
          v -> toBinaryTarget(v)
        ));

    return new TargetMap(binaryTargets);
  }

  private TargetIdeInfo toBinaryTarget(TargetIdeInfo targetIdeInfo) {
    System.out.println(targetIdeInfo);
    TargetIdeInfo.Builder builder = TargetIdeInfo.builder()
      .setLabel("//:main")
      .setKind(Kind.SCALA_BINARY)
      .setBuildFile(tempBuildFileLocation(targetIdeInfo));

    targetIdeInfo.sources.forEach(builder::addSource);

    return builder.build();
  }

  private static ArtifactLocation tempBuildFileLocation(TargetIdeInfo target) {
    ArtifactLocation location = ArtifactLocation.builder()
       .setRelativePath(
         String.format("ijwb_tmp/%d-%s/BUILD",
           target.key.label.hashCode(),
           target.key.label.targetName()
         ))
       .build();
    return location;
  }
}
