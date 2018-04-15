package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.intellij.openapi.project.Project;

public class ScalaBinaryRunConfigurationProducer
  extends BlazeScalaMainClassRunConfigurationProducer {
  private static final String SCALA_BINARY_MAP_KEY = "BlazeScalaBinaryMap";

  public ScalaBinaryRunConfigurationProducer() {
    super(SCALA_BINARY_MAP_KEY);
  }

  protected FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
      project,
      projectData.artifactLocationDecoder,
      projectData.targetMap,
      (target) -> target.kind == Kind.SCALA_BINARY && target.isPlainTarget());
  }
}