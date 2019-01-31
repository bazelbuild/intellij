/*
 * Copyright 2006-2018 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.scala.sync;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt;

import java.util.*;

public class WildCardImportExtractor {


    protected static List<PsiClass> getImportsFromWildCard(@NotNull PsiElement element, String importedPackageName) {
        List<PsiClass> importLinesResult;

        final ImportIssueRecursiveScalaElementVisitor scalaFileVisitor = new ImportIssueRecursiveScalaElementVisitor(importedPackageName);
        importLinesResult = handleScalaFile((ScImportStmt) element, scalaFileVisitor);

        return importLinesResult;
    }

    private static List<PsiClass> handleScalaFile(ScImportStmt element, ImportIssueRecursiveScalaElementVisitor visitor) {
        final ScalaFile scalaFile = (ScalaFile) element.getContainingFile();

        return getImportListForWildCardInScalaFile(scalaFile.getClasses(), visitor);
    }

    private static List<PsiClass> getImportListForWildCardInScalaFile(PsiClass[] classes, ImportIssueRecursiveScalaElementVisitor visitor) {
        for (PsiClass aClass : classes) {
            aClass.accept(visitor);
        }
        final PsiClass[] importedClasses = visitor.getImportedClasses();
        return Arrays.asList(importedClasses);
    }

    private static class ImportIssueRecursiveScalaElementVisitor extends ScalaRecursiveElementVisitor {


        private final String importedPackageName;
        private final Set<PsiClass> importedClasses = new HashSet<>();

        public ImportIssueRecursiveScalaElementVisitor(String importedPackageName) {
            this.importedPackageName = importedPackageName;
        }

        @Override
        public void visitReference(ScReferenceElement reference) {
            super.visitReference(reference);
            PsiElement element = reference.resolve();

            if (element instanceof PsiClass) {
                final PsiClass aClass = (PsiClass) element;

                final String qualifiedName = aClass.getQualifiedName();
                final String packageName =
                        ClassUtil.extractPackageName(qualifiedName);
                if (importedPackageName.equals(packageName)) {
                    importedClasses.add(aClass);
                }
            }
        }

        public PsiClass[] getImportedClasses() {
            return importedClasses.toArray(PsiClass.EMPTY_ARRAY);
        }
    }

}