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

import static com.google.idea.blaze.base.model.blaze.deepequalstester.DeepEqualsTesterUtil.isSubclassOf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Examples used in deep equals tests */
public final class Examples {

  /** pair utility class */
  public static final class Pair<First, Second> {

    private final First first;
    private final Second second;

    public static <First, Second> Pair<First, Second> of(First first, Second second) {
      return new Pair<First, Second>(first, second);
    }

    public Pair(First first, Second second) {
      this.first = first;
      this.second = second;
    }

    First getFirst() {
      return first;
    }

    Second getSecond() {
      return second;
    }
  }

  private static Map<Class<? extends Object>, Pair<? extends Object, ? extends Object>>
      BASE_EXAMPLES;
  private static Map<Class<? extends Object>, Pair<? extends Object, ? extends Object>>
      ARRAY_EXAMPLES;

  private final Map<Class<? extends Object>, Pair<? extends Object, ? extends Object>>
      customExamples;

  static {
    BASE_EXAMPLES = Maps.newHashMap();
    BASE_EXAMPLES.put(String.class, Pair.of("foo", "foobar"));
    BASE_EXAMPLES.put(File.class, Pair.of(new File("a"), new File("b")));
    BASE_EXAMPLES.put(Integer.class, Pair.of(1, 2));
    BASE_EXAMPLES.put(Integer.TYPE, Pair.of(1, 2));
    BASE_EXAMPLES.put(Long.class, Pair.of(1L, 2L));
    BASE_EXAMPLES.put(Long.TYPE, Pair.of(1L, 2L));
    BASE_EXAMPLES.put(Short.class, Pair.of((short) 1, (short) 2));
    BASE_EXAMPLES.put(Short.TYPE, Pair.of((short) 1, (short) 2));
    BASE_EXAMPLES.put(Character.class, Pair.of('a', 'b'));
    BASE_EXAMPLES.put(Character.TYPE, Pair.of('a', 'b'));
    BASE_EXAMPLES.put(Byte.class, Pair.of((byte) 1, (byte) 2));
    BASE_EXAMPLES.put(Byte.TYPE, Pair.of((byte) 1, (byte) 2));
    BASE_EXAMPLES.put(Float.class, Pair.of((float) 1.0, (float) 2.0));
    BASE_EXAMPLES.put(Float.TYPE, Pair.of((float) 1.0, (float) 2.0));
    BASE_EXAMPLES.put(Double.class, Pair.of(1.0, 2.0));
    BASE_EXAMPLES.put(Double.TYPE, Pair.of(1.0, 2.0));
    BASE_EXAMPLES.put(Boolean.class, Pair.of(true, false));
    BASE_EXAMPLES.put(Boolean.TYPE, Pair.of(true, false));
    Map<String, String> mapA = Maps.newHashMap();
    mapA.put("foo", "bar");
    Map<String, String> mapB = Maps.newHashMap();
    mapB.put("tip", "top");
    BASE_EXAMPLES.put(Map.class, Pair.of(mapA, mapB));
    Set<String> setA = new ImmutableSet.Builder<String>().add("A").build();
    Set<String> setB = new ImmutableSet.Builder<String>().add("A").add("B").build();
    BASE_EXAMPLES.put(Set.class, Pair.of(setA, setB));
    BASE_EXAMPLES.put(Collection.class, Pair.of(setA, setB));
    Builder<String> listABuilder = ImmutableList.builder();
    List<String> listA = listABuilder.add("A").build();
    Builder<String> listBBuilder = ImmutableList.builder();
    List<String> listB = listBBuilder.add("A").add("B").build();
    BASE_EXAMPLES.put(List.class, Pair.of(listA, listB));

    ARRAY_EXAMPLES = Maps.newHashMap();
    int[] intArrA = {1, 2};
    int[] intArrB = {3, 4};
    ARRAY_EXAMPLES.put(Integer.TYPE, Pair.of(intArrA, intArrB));
    long[] longArrA = {1, 2};
    long[] longArrB = {3, 4};
    ARRAY_EXAMPLES.put(Long.TYPE, Pair.of(longArrA, longArrB));
    short[] shortArrA = {1, 2};
    short[] shortArrB = {3, 4};
    ARRAY_EXAMPLES.put(Short.TYPE, Pair.of(shortArrA, shortArrB));
    char[] charArrA = {'a', 'b'};
    char[] charArrB = {'c', 'd'};
    ARRAY_EXAMPLES.put(Character.TYPE, Pair.of(charArrA, charArrB));
    byte[] byteArrA = {1, 2};
    byte[] byteArrB = {3, 4};
    ARRAY_EXAMPLES.put(Byte.TYPE, Pair.of(byteArrA, byteArrB));
    boolean[] boolArrA = {true, false};
    boolean[] boolArrB = {false, false};
    ARRAY_EXAMPLES.put(Boolean.TYPE, Pair.of(boolArrA, boolArrB));
    float[] floatArrA = {1.0f, 2.0f};
    float[] floatArrB = {3.0f, 4.0f};
    ARRAY_EXAMPLES.put(Float.TYPE, Pair.of(floatArrA, floatArrB));
    double[] doubleArrA = {1.0, 2.0};
    double[] doubleArrB = {3.0, 4.0};
    ARRAY_EXAMPLES.put(Double.TYPE, Pair.of(doubleArrA, doubleArrB));
  }

  /** Thrown when an example could not be found */
  public static class ExampleNotFoundException extends Exception {

    private final Class<?> clazz;

    public ExampleNotFoundException(Class<?> clazz) {
      this.clazz = clazz;
    }

    @Override
    public String getMessage() {
      return "Could not find example for: " + clazz.toString();
    }
  }

  public Examples() {
    this.customExamples = Maps.newHashMap();
  }

  public void addExample(Class<? extends Object> clazz, Object a, Object b) {
    customExamples.put(clazz, Pair.of(a, b));
  }

  public <T extends Serializable> Pair<? extends Object, ? extends Object> getExamples(
      @NotNull Class<T> clazz) throws ExampleNotFoundException {
    if (customExamples.containsKey(clazz)) {
      return customExamples.get(clazz);
    }
    if (BASE_EXAMPLES.containsKey(clazz)) {
      return BASE_EXAMPLES.get(clazz);
    }
    // Special case subclasses of collections
    if (isSubclassOf(clazz, Set.class)) {
      return BASE_EXAMPLES.get(Set.class);
    }
    if (isSubclassOf(clazz, List.class)) {
      return BASE_EXAMPLES.get(List.class);
    }
    if (isSubclassOf(clazz, Collection.class)) {
      return BASE_EXAMPLES.get(Collection.class);
    }

    // If we have an array of Objects, we have to do a little trickery to create one we can swap
    // in
    if (clazz.isArray()) {
      Class<?> arrayType = clazz.getComponentType();
      if (!arrayType.isPrimitive()) {
        Pair<?, ?> examples = getExamples((Class<? extends Serializable>) arrayType);
        Object arrayA = Array.newInstance(arrayType, 1);
        Array.set(arrayA, 0, examples.getFirst());
        Object arrayB = Array.newInstance(arrayType, 1);
        Array.set(arrayB, 0, examples.getSecond());
        return Pair.of(arrayA, arrayB);
      }
      if (ARRAY_EXAMPLES.containsKey(arrayType)) {
        return ARRAY_EXAMPLES.get(arrayType);
      }
    }

    throw new ExampleNotFoundException(clazz);
  }
}
