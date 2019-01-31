package com.google.idea.blaze.ijwb;

import com.google.idea.blaze.base.ui.problems.ImportLineUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.ImportType.*;
import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.getImportType;
import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.getPackageName;
import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class ImportLineUtilsTest {

    //getPackageName
    @Test
    public void getPackageName_regularImportLine() {
        String importLine = "import org.junit.Test;";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("org.junit", packageName.get());
    }

    @Test
    public void getPackageName_javaWildCardImportLine() {
        String importLine = "import org.junit.*;";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("org.junit", packageName.get());
    }

    @Test
    public void getPackageName_scalaWildCardImportLine() {
        String importLine = "import org.junit._";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("org.junit", packageName.get());
    }


    @Test
    public void getPackageName_scalaMultipleClassesImportLine() {
        String importLine = "import java.util.concurrent.{CountDownLatch, Executors}";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("java.util.concurrent", packageName.get());
    }

    @Test
    public void getPackageName_mixedScalaMultipleClassesWithAliasImportLine() {
        String importLine = "import java.util.concurrent.{CountDownLatch, Executors, A=> B}";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("java.util.concurrent", packageName.get());
    }

    @Test
    public void getPackageName_scalaAliasImport() {
        String importLine = "import java.lang.{Boolean => JBool, Integer => JInt}";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("java.lang", packageName.get());
    }

    @Test
    public void getPackageName_javaStaticImport() {
        String importLine = "import static com.google.common.collect.Lists.newArrayList;";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals("com.google.common.collect", packageName.get());
    }

    //getImportType
    @Test
    public void getImportType_regularImportLine() {
        String importLine = "import org.junit.Test;";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(REGULAR, importType);
    }

    @Test
    public void getImportType_javaWildCardImportLine() {
        String importLine = "import org.junit.*;";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(JAVA_WILDCARD, importType);
    }

    @Test
    public void getImportType_scalaWildCardImportLine() {
        String importLine = "import org.junit._";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(SCALA_WILDCARD, importType);
    }


    @Test
    public void getImportType_scalaMultipleClassesImportLine() {
        String importLine = "import java.util.concurrent.{CountDownLatch, Executors}";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(MULTIPLE_SCALA, importType);
    }

    @Test
    public void getImportType_mixedScalaMultipleClassesWithAliasImportLine() {
        String importLine = "import java.util.concurrent.{CountDownLatch, Executors, A=> B}";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(SCALA_ALIAS, importType);
    }

    @Test
    public void getImportType_scalaAliasImport() {
        String importLine = "import java.lang.{Boolean => JBool, Integer => JInt}";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(SCALA_ALIAS, importType);
    }

    @Test
    public void getImportType_javaStaticImport() {
        String importLine = "import static com.google.common.collect.Lists.newArrayList;";

        ImportLineUtils.ImportType importType = getImportType(importLine);

        assertEquals(JAVA_STATIC, importType);
    }
}