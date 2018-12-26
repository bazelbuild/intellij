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
package com.google.idea.blaze.base.ui.problems;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor;
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt;

import java.util.*;

public class WildCardImportExtractor {


    public static final String JAVA_SUFFIX = "java";
    public static final String SCALA_SUFFIX = "scala";

    protected static List<PsiClass> getImportsFromWildCard(@NotNull PsiElement element, String importedPackageName) {
        String fileName = element.getContainingFile().getName();
        List<PsiClass> importLinesResult = Lists.newArrayList();

        final JavaClassCollector javaVisitor = new JavaClassCollector(importedPackageName);
        final ImportIssueRecursiveScalaElementVisitor scalaFileVisitor = new ImportIssueRecursiveScalaElementVisitor(importedPackageName);
        if (fileName.endsWith(JAVA_SUFFIX)) {
            importLinesResult = handleJavaFile((PsiImportStatementBase) element, javaVisitor);
        } else if (fileName.endsWith(SCALA_SUFFIX)) {
            importLinesResult = handleScalaFile((ScImportStmt) element, scalaFileVisitor);
        }

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

    private static List<PsiClass> handleJavaFile(@NotNull PsiImportStatementBase importStatementBase, JavaClassCollector visitor) {
        final PsiJavaFile javaFile = (PsiJavaFile) importStatementBase.getContainingFile();
        if (importStatementBase instanceof PsiImportStatement) {
            return getImportListForWildCardInJavaFile(javaFile, visitor);
        } else {
            return Lists.newArrayList();
        }
    }

    @NotNull
    private static List<PsiClass> getImportListForWildCardInJavaFile(PsiJavaFile javaFile, JavaClassCollector visitor) {
        final PsiClass[] classes = javaFile.getClasses();
        for (PsiClass aClass : classes) {
            aClass.accept(visitor);
        }
         PsiClass[] importedClasses = visitor.getImportedClasses();
        return Arrays.asList(importedClasses);
    }

    private static class JavaClassCollector extends JavaRecursiveElementWalkingVisitor {

        private final String importedPackageName;
        private final Set<PsiClass> importedClasses = new HashSet<>();

        JavaClassCollector(String importedPackageName) {
            this.importedPackageName = importedPackageName;
        }

        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
            if (reference.isQualified()) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass) element;
            final String qualifiedName = aClass.getQualifiedName();
            final String packageName =
                    ClassUtil.extractPackageName(qualifiedName);
            if (importedPackageName.equals(packageName)) {
                importedClasses.add(aClass);
            }

        }

        public PsiClass[] getImportedClasses() {
            return importedClasses.toArray(PsiClass.EMPTY_ARRAY);
        }
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