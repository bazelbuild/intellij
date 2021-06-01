package com.google.idea.blaze.base.run.producer;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Objects;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunLineMarkerProvider;
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeBuildFileRunLineMarkerProvider} */
@RunWith(JUnit4.class)
public class BlazeBuildFileRunLineMarkerProviderTest extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testFunCallInfo() {
    BlazeBuildFileRunLineMarkerProvider markerContributor =
        new BlazeBuildFileRunLineMarkerProvider();

    PsiFile buildFile =
        createAndIndexFile(WorkspacePath.createIfValid("BUILD"), "java_library(name = 'bar')");
    List<LeafPsiElement> elements =
        PsiUtils.findAllChildrenOfClassRecursive(buildFile, LeafPsiElement.class);
    LeafPsiElement funCallIdentifier =
        elements.stream()
            .filter(e -> Objects.equal(e.getText(), "java_library"))
            .findFirst()
            .orElse(null);
    assertThat(funCallIdentifier).isNotNull();

    Info objectInfo = markerContributor.getInfo(funCallIdentifier);
    assertThat(objectInfo).isNotNull();
    assertThat(objectInfo.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run);
  }
}
