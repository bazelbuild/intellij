/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.projectview.documentation;

import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewSection;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

/** Provides quick docs for some .blazeproject elements. */
public class ProjectViewDocumentationProvider extends AbstractDocumentationProvider
    implements ExternalDocumentationProvider {

  @Nullable
  private static SectionParser getSection(PsiElement element) {
    ProjectViewSection section = PsiUtils.getParentOfType(element, ProjectViewSection.class, false);
    return section != null ? section.getSectionParser() : null;
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    SectionParser section = getSection(element);
    if (section == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    String quickDocs = section.quickDocs();
    if (quickDocs != null) {
      builder.append(wrapInTag("<p>" + section.quickDocs(), "code"));
    }
    String url = getUrlFor(element.getProject(), section, false);
    if (url != null) {
      builder.append(
          String.format("<p><b>External documentation</b>:<br><a href=\"%s\">%s</a>", url, url));
    }
    return wrapInTag(wrapInTag(builder.toString(), "body"), "html");
  }

  private static String wrapInTag(String doc, String htmlTag) {
    return String.format("<%s>%s</%s>", htmlTag, doc, htmlTag);
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return getUrlFor(element, false) != null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    final String url = getUrlFor(element, true);
    return url == null ? null : Collections.singletonList(url);
  }

  @Nullable
  @Override
  public String fetchExternalDocumentation(
      Project project, PsiElement element, List<String> docUrls) {
    return null;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {}

  @Nullable
  private static String getUrlFor(PsiElement element, boolean checkExistence) {
    SectionParser section = getSection(element);
    return section != null ? getUrlFor(element.getProject(), section, checkExistence) : null;
  }

  @Nullable
  private static String getUrlFor(Project project, SectionParser section, boolean checkExistence) {
    String baseDocsUrl = Blaze.getBuildSystemProvider(project).getProjectViewDocumentationUrl();
    if (baseDocsUrl == null) {
      return null;
    }
    String url = baseDocsUrl + "#" + section.getName();
    if (checkExistence && !pageExists(url)) {
      return baseDocsUrl;
    }
    return url;
  }

  private static boolean pageExists(String url) {
    final HttpClient client = new HttpClient();
    final HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
    params.setSoTimeout(5 * 1000);
    params.setConnectionTimeout(5 * 1000);

    try {
      final HeadMethod method = new HeadMethod(url);
      final int rc = client.executeMethod(method);
      if (rc == 404) {
        return false;
      }
    } catch (IllegalArgumentException e) {
      return false;
    } catch (IOException e) {
      // ignore
    }
    return true;
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(
      Editor editor, PsiFile file, @Nullable PsiElement contextElement) {
    return PsiUtils.getParentOfType(contextElement, ProjectViewSection.class, false);
  }
}
