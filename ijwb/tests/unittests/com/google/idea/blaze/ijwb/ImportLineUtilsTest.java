package com.google.idea.blaze.ijwb;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.getPackageName;
import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class ImportLineUtilsTest {

    @Test
    public void getPackageName_regularImportLine() {
        String importLine = "import org.junit.Test;";

        String packageName = getPackageName(importLine);

        assertEquals("org.junit", packageName);
    }

    @Test
    public void getPackageName_javaWildCardImportLine() {
        String importLine = "import org.junit.*;";

        String packageName = getPackageName(importLine);

        assertEquals("org.junit", packageName);
    }

    @Test
    public void getPackageName_scalaWildCardImportLine() {
        String importLine = "import org.junit._";

        String packageName = getPackageName(importLine);

        assertEquals("org.junit", packageName);
    }


    @Test
    public void getPackageName_scalaMultipleClassesImportLine() {
        String importLine = "import java.util.concurrent.{CountDownLatch, Executors}";

        String packageName = getPackageName(importLine);

        assertEquals("java.util.concurrent", packageName);
    }

}