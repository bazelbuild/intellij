package com.google.idea.common.aquery

import com.google.common.truth.Truth.assertThat
import com.google.devtools.build.lib.analysis.AnalysisProtosV2
import com.google.idea.testing.runfiles.Runfiles
import com.google.protobuf.TextFormat
import org.junit.Before
import java.io.InputStreamReader
import java.nio.file.Files
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ActionGraphTest {

  private lateinit var graph: ActionGraph

  @Before
  fun setUp() {
    val path = Runfiles.runfilesPath("common/aquery/fixture.textproto")
    graph = ActionGraph.fromProto(Files.readString(path))
  }

  @Test
  fun targets_returnsSingleTarget() {
    assertThat(graph.targets.toList()).hasSize(1)
  }

  @Test
  fun defaultTarget_ruleClass() {
    assertThat(graph.defaultTarget.ruleClass).isEqualTo("cc_binary")
  }

  @Test
  fun defaultTarget_hasAllActions() {
    val mnemonics = graph.defaultTarget.actions.map { it.mnemonic }.toList()
    assertThat(mnemonics).containsAtLeast("CppCompile", "CppLink")
  }

  @Test
  fun action_configuration() {
    val action = graph.defaultTarget.actions.first()
    assertThat(action.configuration).isEqualTo("91e24984663c795f7d9f904758d4b84ba1954389b2ab756f70a6f32db2a79020")
  }

  @Test
  fun action_targetPointsBackToSameTarget() {
    val action = graph.defaultTarget.actions.first()
    assertThat(action.target.ruleClass).isEqualTo("cc_binary")
  }

  @Test
  fun cppCompileAction_outputs() {
    val action = graph.defaultTarget.actions.first { it.mnemonic == "CppCompile" }
    val outputPaths = action.outputs.map { it.path.toString() }.toList()

    assertThat(outputPaths).containsExactly(
      "bazel-out/k8-fastbuild/bin/main/_objs/main_cc/main.pic.o",
      "bazel-out/k8-fastbuild/bin/main/_objs/main_cc/main.pic.d",
    )
  }

  @Test
  fun cppLinkAction_output() {
    val action = graph.defaultTarget.actions.first { it.mnemonic == "CppLink" }
    val outputPaths = action.outputs.map { it.path.toString() }.toList()

    assertThat(outputPaths).containsExactly(
      "bazel-out/k8-fastbuild/bin/main/main_cc",
    )
  }

  @Test
  fun target_allOutputs() {
    val allOutputs = graph.defaultTarget.actions.flatMap { it.outputs }.map { it.path.toString() }.toList()

    assertThat(allOutputs).containsAtLeast(
      "bazel-out/k8-fastbuild/bin/main/_objs/main_cc/main.pic.o",
      "bazel-out/k8-fastbuild/bin/main/_objs/main_cc/main.pic.d",
      "bazel-out/k8-fastbuild/bin/main/main_cc",
    )
  }
}
