/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.model.blaze.deepequalstester;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.blaze.deepequalstester.Examples.ExampleNotFoundException;
import com.google.idea.blaze.base.model.blaze.deepequalstester.Examples.Pair;
import com.google.idea.blaze.base.model.blaze.deepequalstester.ReachabilityAnalysis.ReachableClasses;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class DeepEqualsTester {

  public static class TestCorrectnessException extends Exception {

    public TestCorrectnessException(String s) {
      super(s);
    }
  }

  /**
   * Ensure that the equals method of {@param rootObject} uses all of its fields in its comparison.
   * Recurse into every field of {@param rootObject} to ensure that they also use all of their
   * fields in their equals method. Continue recursion until primitives are hit.
   * <p/>
   * If multiple failures could occur, there is no guarantee that they will always occur in the same
   * order. Only the first failure is reported.
   *
   * @param rootObject an example instantiation of the class we want to test for deep equals sanity.
   *                   The object must be a standard java object (no collections, no arrays, no primitives). If you
   *                   would like to pass these types in, you should put them in a box first.
   * @param examples   examples of objects to use for comparison. This should contain a pair of
   *                   examples for every type that could be reachable from the root object. This value may be
   *                   mutated by this test.
   */
  public static <T extends Serializable> void doDeepEqualsAndHashTest(@NotNull T rootObject,
                                                                      Examples examples)
    throws InstantiationException, IllegalAccessException, NoSuchFieldException, ExampleNotFoundException, TestCorrectnessException {
    ReachableClasses reachableClasses = new ReachableClasses();

    try {
      ArrayList<String> initialPath = Lists.newArrayList("root");
      // Find all of the classes reachable from the root object. This is not sound since it
      // ignores subtypes (or supertypes) that could be used
      ReachabilityAnalysis
        .computeReachableFromObject(rootObject, rootObject.getClass(), initialPath,
                                    reachableClasses);
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    // Add the root object to our list of reachable classes so we can do all the testing in one
    // loop
    reachableClasses.addPath(rootObject.getClass(), Lists.newArrayList("root"));
    // In our situations, we never need a second example of the root object
    examples.addExample(rootObject.getClass(), rootObject, rootObject);
    // For each reachable class, do a shallow equals test where we change each value of the
    // object one at a time and test for equality
    for (Class<? extends Serializable> clazz : reachableClasses.getClasses()) {
      Serializable workitem = (Serializable)examples.getExamples(clazz).getFirst();
      testShallowEquals(workitem, reachableClasses, examples);
    }
  }

  private static String getFailureMessage(String method, Field field, List<String> examplePath) {
    StringBuilder sb = new StringBuilder();
    sb.append(field.toString()).append(" is not represented in it's parent's ")
      .append(method).append(" method\n");
    for (String path : examplePath) {
      sb.append("\t").append(path).append("\n");
    }
    sb.append("\n");
    return sb.toString();
  }

  /**
   * Mutate each field in the object one at a time and test for equality of the object. Assert a
   * failure if any mutation doesn't result in the two objects not being equal
   */
  private static <T extends Serializable> void testShallowEquals(@NotNull T original,
                                                                 ReachableClasses reachableClasses, Examples examples)
    throws ExampleNotFoundException, IllegalAccessException, TestCorrectnessException {
    T clone = (T)DeepEqualsTesterUtil.cloneWithSerialization(original);
    List<Field> allFields = DeepEqualsTesterUtil.getAllFields(original.getClass());
    for (Field field : allFields) {
      if (!Modifier.isStatic(field.getModifiers())) {
        field.setAccessible(true);
        Pair<?, ?> examplesPair = examples.
          getExamples((Class<? extends Serializable>)field.getType());
        Object newValueForOriginal = examplesPair.getFirst();
        Object newValueForClone = examplesPair.getSecond();
        Object oldValueForOriginal = field.get(original);
        Object oldValueForClone = field.get(clone);
        // Ensure that the two objects really are equal before we tweak them
        boolean objectsTheSameBeforeTweak = original.equals(clone);
        if (!objectsTheSameBeforeTweak) {
          throw new TestCorrectnessException(
            "original was not equal to clone before tweaking them");
        }
        boolean objectsHashTheSameBeforeTweak = original.hashCode() == clone.hashCode();
        if (!objectsHashTheSameBeforeTweak) {
          throw new TestCorrectnessException(
            "original hash code was not equal to clone hash code before tweaking the objects");
        }
        field.set(original, newValueForOriginal);
        field.set(clone, newValueForClone);
        boolean equalsWorksAsIntended = !original.equals(clone);
        boolean hashWorksAsIntended = original.hashCode() != clone.hashCode();
        // Return to our original state before possibly failing
        field.set(original, oldValueForOriginal);
        field.set(clone, oldValueForClone);
        Assert.assertTrue(getFailureMessage("equals", field, reachableClasses.getExamplePathTo(
          original.getClass())), equalsWorksAsIntended);
        Assert.assertTrue(getFailureMessage("hash", field, reachableClasses.getExamplePathTo(
          original.getClass())), hashWorksAsIntended);
      }
    }
  }
}
