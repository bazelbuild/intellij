package com.google.idea.blaze.golang.run.producers;

import com.google.idea.blaze.base.BlazeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class GoTestContextProviderTest extends BlazeTestCase {
    @Test
    public void testRegexifyTestFilter() {
        String regexified = GoTestContextProvider.regexifyTestFilter("Test/with/subtest(good \\ or \\ bad)");
        assertThat(regexified).isEqualTo("^Test/with/subtest\\(good \\\\ or \\\\ bad\\)$");
    }
}
