/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.lang.buildfile.references;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AttributeSpecificStringLiteralReferenceProviderTest extends BuildFileIntegrationTestCase {
    @Test
    public void testFindReferencesWithMultipleProviders() {
        registerExtension(AttributeSpecificStringLiteralReferenceProvider.EP_NAME, new MockProvider("srcs"));
        registerExtension(AttributeSpecificStringLiteralReferenceProvider.EP_NAME, new MockProvider("srcs"));
        registerExtension(AttributeSpecificStringLiteralReferenceProvider.EP_NAME, new MockProvider("hdrs"));

        WorkspacePath wp = new WorkspacePath("java/com/google/BUILD");
        String content = "\"//something\""; // The content is not important, we only need a string literal.
        BuildFile file = createBuildFile(wp, content);
        StringLiteral literal = PsiUtils.findFirstChildOfClassRecursive(file, StringLiteral.class);

        PsiReference[] results = AttributeSpecificStringLiteralReferenceProvider.findReferences("srcs", literal);
        assertThat(results).hasLength(2); // The first and second providers should contribute one reference each.
    }

    private static class MockProvider implements AttributeSpecificStringLiteralReferenceProvider {
        String attributeName;

        public MockProvider(String attributeName) {
            this.attributeName = attributeName;
        }

        @Override
        public PsiReference[] getReferences(String attributeName, StringLiteral literal) {
            if (!this.attributeName.equals(attributeName)) {
                return PsiReference.EMPTY_ARRAY;
            }
            return new PsiReference[]{new LabelReference(literal, true)};
        }
    }
}
