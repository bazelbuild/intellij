package com.google.idea.blaze.ijwb;

import com.google.idea.blaze.base.ui.problems.ImportLineUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(JUnit4.class)
public class ImportLineUtilsTest {

    @Test
    public void getPackageName_regularImportLine() {
        String importLine = "import org.junit.Test;";

        String packageName = ImportLineUtils.getPackageName(importLine);

        assertEquals("org.junit", packageName);
    }

    @Test
    public void getPackageName_javaWildCardImportLine() {
        String importLine = "import org.junit.*;";

        String packageName = ImportLineUtils.getPackageName(importLine);

        assertEquals("org.junit", packageName);
    }

    @Test
    public void getPackageName_scalaWildCardImportLine() {
        String importLine = "import org.junit._";

        String packageName = ImportLineUtils.getPackageName(importLine);

        assertEquals("org.junit", packageName);
    }


}