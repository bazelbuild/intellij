package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches.Format;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransitionTest extends ClwbHeadlessTestCase {

  // configuration ids are only exposed for Bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = super.projectViewText(version);

    builder.addTarget("//main:foo_bar");

    return builder;
  }

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkResolveConfigurations();
    checkTargetMap();
    checkConfigurationData();
  }

  /**
   * The split transition builds simple.cc in two configurations (foo and bar),
   * so the workspace should have two resolve configurations for that file.
   */
  private void checkResolveConfigurations() {
    final var file = findProjectFile("main/simple.cc");

    final var configurations = getWorkspace().getConfigurationsForFile(file);
    assertThat(configurations).hasSize(2);

    final var compilerSwitches = configurations.stream()
        .map(config -> config.getCompilerSettings(CLanguageKind.CPP).getCompilerSwitches())
        .filter(Objects::nonNull)
        .map(switches -> switches.getList(Format.RAW))
        .toList();

    assertThat(compilerSwitches).hasSize(2);
    assertThat(compilerSwitches.stream().filter(it -> it.contains("-DFOO"))).hasSize(1);
    assertThat(compilerSwitches.stream().filter(it -> it.contains("-DBAR"))).hasSize(1);
  }

  /**
   * The target map should contain two entries for //main:simple with different configuration IDs.
   */
  private void checkTargetMap() {
    final var projectData = BlazeProjectDataManager.getInstance(myProject).getBlazeProjectData();
    assertThat(projectData).isNotNull();

    final var targets = projectData.targetMap().get(Label.create("//main:simple"));
    assertThat(targets).hasSize(2);

    final var configurationIds = targets.stream()
        .map(t -> t.getKey().configurationId())
        .collect(Collectors.toSet());

    // the two targets must have distinct, non-empty configuration IDs
    assertThat(configurationIds).hasSize(2);
    for (final var id : configurationIds) {
      assertThat(id).isNotEmpty();
    }
  }

  /**
   * The configuration data should contain entries for the configuration IDs found in the target map.
   */
  private void checkConfigurationData() {
    final var projectData = BlazeProjectDataManager.getInstance(myProject).getBlazeProjectData();
    assertThat(projectData).isNotNull();

    final var configurationData = projectData.configurationData();
    assertThat(configurationData.configurations).isNotEmpty();

    final var targets = projectData.targetMap().get(Label.create("//main:simple"));
    for (final var target : targets) {
      assertThat(configurationData.get(target.getKey())).isNotNull();
    }
  }
}
