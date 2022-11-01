package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ScalatestTestSelectorFlagsModificationTest {

    private RunConfigurationFlagsState createStateForFlags(List<String> flags) {
        RunConfigurationFlagsState state = new RunConfigurationFlagsState("", "");
        state.setRawFlags(flags);
        return state;
    }

    @Test
    public void canModifyFlagsWithTestName() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test name");
        List<String> flags = new ArrayList<>();

        modifier.modifyFlags(flags);

        assertThat(flags.size()).isEqualTo(4);
        assertThat(flags).containsExactly("--test_arg=-s", "--test_arg=com.google.TestClass", "--test_arg=-t", "--test_arg=\"some test name\"").inOrder();
        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isTrue();
    }

    @Test
    public void canModifyFlagsWithoutTestName() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", null);
        List<String> flags = new ArrayList<>();

        modifier.modifyFlags(flags);

        assertThat(flags.size()).isEqualTo(2);
        assertThat(flags).containsExactly("--test_arg=-s", "--test_arg=com.google.TestClass").inOrder();
        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isTrue();
    }

    @Test
    public void canHandleQuotesInTestName() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test\" name");
        List<String> flags = new ArrayList<>();

        modifier.modifyFlags(flags);

        assertThat(flags.size()).isEqualTo(4);
        assertThat(flags).containsExactly("--test_arg=-s", "--test_arg=com.google.TestClass", "--test_arg=-t", "--test_arg=\"some test\\\" name\"").inOrder();
        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isTrue();
    }

    @Test
    public void canHandleNewlineSplitTestNames() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test name\nsome other test name");
        List<String> flags = new ArrayList<>();

        modifier.modifyFlags(flags);

        assertThat(flags.size()).isEqualTo(6);
        assertThat(flags).containsExactly(
            "--test_arg=-s", "--test_arg=com.google.TestClass",
            "--test_arg=-t", "--test_arg=\"some test name\"",
            "--test_arg=-t", "--test_arg=\"some other test name\""
            );
        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isTrue();
    }

    @Test
    public void matchesConfigStateOnlyIfFlagsOccurInRightOrder() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test name");
        List<String> flags = new ArrayList<>();
        flags.add("--test_arg=-t");
        flags.add("--test_arg=\"some test name\"");
        flags.add("--test_arg=-s");
        flags.add("--test_arg=com.google.TestClass");

        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isFalse();
    }

    @Test
    public void matchesConfigStateOnlyIfFlagsOccurInRightOrderWithoutTestName() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", null);
        List<String> flags = new ArrayList<>();
        flags.add("--test_arg=com.google.TestClass");
        flags.add("--test_arg=-s");

        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isFalse();
    }

    @Test
    public void canHandleOtherFlagsInInput() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifier =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test name");
        List<String> flags = new ArrayList<>();
        flags.add("--other_flag");
        flags.add("--other_flag_2");

        modifier.modifyFlags(flags);

        flags.add("--yet_another_flag");

        assertThat(modifier.matchesConfigState(createStateForFlags(flags))).isTrue();
        assertThat(flags.subList(2, 6)).containsExactly("--test_arg=-s", "--test_arg=com.google.TestClass", "--test_arg=-t", "--test_arg=\"some test name\"").inOrder();
    }

    @Test
    public void flagsForRunningSingleTestShouldNotMatchFlagsForRunningClass() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifierForSingleTest =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test name");
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifierForClass =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", null);
        List<String> flags = new ArrayList<>();

        modifierForSingleTest.modifyFlags(flags);

        assertThat(modifierForSingleTest.matchesConfigState(createStateForFlags(flags))).isTrue();
        assertThat(modifierForClass.matchesConfigState(createStateForFlags(flags))).isFalse();
    }

    @Test
    public void flagsForRunningClassShouldNotMatchFlagsForRunningSingleTest() {
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifierForSingleTest =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", "some test name");
        ScalaTestContextProvider.ScalatestTestSelectorFlagsModification modifierForClass =
            new ScalaTestContextProvider.ScalatestTestSelectorFlagsModification("com.google.TestClass", null);
        List<String> flags = new ArrayList<>();

        modifierForClass.modifyFlags(flags);

        assertThat(modifierForClass.matchesConfigState(createStateForFlags(flags))).isTrue();
        assertThat(modifierForSingleTest.matchesConfigState(createStateForFlags(flags))).isFalse();
    }

}
