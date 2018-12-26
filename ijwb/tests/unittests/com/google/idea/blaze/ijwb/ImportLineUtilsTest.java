package com.google.idea.blaze.ijwb;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.getPackageName;
import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class ImportLineUtilsTest {

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
    public void getPackageName_StaticImport() {
        String importLine = "import com.wixpress.dispatch.domain.Dispatch.www\n";

        Optional<String> packageName = getPackageName(importLine);

        assertEquals(Optional.empty(), packageName);
    }

}