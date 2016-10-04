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
package com.google.idea.blaze.base.model.blaze;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.blaze.deepequalstester.DeepEqualsTester;
import com.google.idea.blaze.base.model.blaze.deepequalstester.DeepEqualsTester.TestCorrectnessException;
import com.google.idea.blaze.base.model.blaze.deepequalstester.DeepEqualsTesterUtil;
import com.google.idea.blaze.base.model.blaze.deepequalstester.Examples;
import com.google.idea.blaze.base.model.blaze.deepequalstester.Examples.ExampleNotFoundException;
import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests to verify that our equals tester is working correctly */
@RunWith(JUnit4.class)
public class DeepEqualsTesterTest {

  // The equals method does not work correctly if T is an array
  private static class Box<T> implements Serializable {

    public T data;

    public Box(T data) {
      this.data = data;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Box<?> box = (Box<?>) o;
      return Objects.equal(data, box.data);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(data);
    }
  }

  private static class CorrectEqualsAndHash implements Serializable {

    public String name;

    public CorrectEqualsAndHash(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CorrectEqualsAndHash foo = (CorrectEqualsAndHash) o;
      return Objects.equal(name, foo.name);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name);
    }
  }

  private static class ClassWithCorrectEqualsMember implements Serializable {

    public String myName;
    public CorrectEqualsAndHash myCorrectEqualsAndHash;

    public ClassWithCorrectEqualsMember(String name, String innerName) {
      this.myName = name;
      this.myCorrectEqualsAndHash = new CorrectEqualsAndHash(innerName);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ClassWithCorrectEqualsMember that = (ClassWithCorrectEqualsMember) o;
      return Objects.equal(myName, that.myName)
          && Objects.equal(myCorrectEqualsAndHash, that.myCorrectEqualsAndHash);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myName, myCorrectEqualsAndHash);
    }
  }

  private static class IncorrectHash implements Serializable {

    public String name;
    public int num;

    public IncorrectHash(String name, int num) {
      this.name = name;
      this.num = num;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IncorrectHash foo = (IncorrectHash) o;
      return Objects.equal(name, foo.name) && Objects.equal(num, foo.num);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name);
    }
  }

  private static class IncorrectEqualsAndHash implements Serializable {

    public String name;
    public int num;

    public IncorrectEqualsAndHash(String name, int num) {
      this.name = name;
      this.num = num;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IncorrectEqualsAndHash foo = (IncorrectEqualsAndHash) o;
      return Objects.equal(name, foo.name);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name);
    }
  }

  private static class ClassWithIncorrectEqualsMember implements Serializable {

    public String myName;
    public IncorrectEqualsAndHash myIncorrectEqualsAndHash;

    public ClassWithIncorrectEqualsMember(String name, String innerName, int innerNum) {
      this.myName = name;
      this.myIncorrectEqualsAndHash = new IncorrectEqualsAndHash(innerName, innerNum);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ClassWithIncorrectEqualsMember that = (ClassWithIncorrectEqualsMember) o;
      return Objects.equal(myName, that.myName)
          && Objects.equal(myIncorrectEqualsAndHash, that.myIncorrectEqualsAndHash);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myName, myIncorrectEqualsAndHash);
    }
  }

  private static class IncorrectEqualsWithArray implements Serializable {

    public IncorrectEqualsAndHash[] array;

    public IncorrectEqualsWithArray(IncorrectEqualsAndHash[] array) {
      this.array = array;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IncorrectEqualsWithArray that = (IncorrectEqualsWithArray) o;
      return Arrays.equals(array, that.array);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode((Object[]) array);
    }
  }

  private static class SubIncorrectEqualsAndHash extends IncorrectEqualsAndHash {

    public Long num;

    public SubIncorrectEqualsAndHash(String name, int iNum, Long num) {
      super(name, iNum);
      this.num = num;
    }
  }

  private static enum ENUMS {
    ONE,
    TWO,
    THREE
  }

  private static class DeepClass<T> implements Serializable {

    public ENUMS myEnum;
    public char myC;
    public T data;
    public File f;

    public DeepClass(ENUMS myEnum, char c, T data, File f) {
      this.myEnum = myEnum;
      this.myC = c;
      this.data = data;
      this.f = f;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DeepClass<?> deepClass = (DeepClass<?>) o;
      return Objects.equal(myC, deepClass.myC)
          && Objects.equal(myEnum, deepClass.myEnum)
          && Objects.equal(data, deepClass.data)
          && Objects.equal(f, deepClass.f);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myEnum, myC, data, f);
    }
  }

  private static class SimpleClassWithSet implements Serializable {

    public Set<File> files;

    public SimpleClassWithSet(Set<File> files) {
      this.files = files;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SimpleClassWithSet that = (SimpleClassWithSet) o;
      return Objects.equal(files, that.files);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(files);
    }
  }

  private static class MapWithIncorrectKey implements Serializable {

    public Map<IncorrectEqualsAndHash, File> files;

    public MapWithIncorrectKey(Map<IncorrectEqualsAndHash, File> files) {
      this.files = files;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MapWithIncorrectKey that = (MapWithIncorrectKey) o;
      return Objects.equal(files, that.files);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(files);
    }
  }

  private static class MapWithIncorrectValue implements Serializable {

    public Map<String, IncorrectEqualsAndHash> map;

    public MapWithIncorrectValue(Map<String, IncorrectEqualsAndHash> map) {
      this.map = map;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MapWithIncorrectValue that = (MapWithIncorrectValue) o;
      return Objects.equal(map, that.map);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(map);
    }
  }

  private static class MapWithCorrectKeyAndValues implements Serializable {

    public Map<String, String> map;

    public MapWithCorrectKeyAndValues(Map<String, String> map) {
      this.map = map;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MapWithCorrectKeyAndValues that = (MapWithCorrectKeyAndValues) o;
      return Objects.equal(map, that.map);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(map);
    }
  }

  private Examples testExamples;

  @Before
  public void populateExtraExamples() {
    testExamples = new Examples();
    testExamples.addExample(
        CorrectEqualsAndHash.class, new CorrectEqualsAndHash("A"), new CorrectEqualsAndHash("B"));
    testExamples.addExample(
        IncorrectEqualsAndHash.class,
        new IncorrectEqualsAndHash("A", 100),
        new IncorrectEqualsAndHash("A", 200));
    testExamples.addExample(
        IncorrectHash.class, new IncorrectHash("A", 100), new IncorrectHash("A", 200));
  }

  @Test
  public void testCorrectEqualsAndHashPassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    CorrectEqualsAndHash myFoo = new CorrectEqualsAndHash("test");
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectEqualsFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectEqualsAndHash myFoo = new IncorrectEqualsAndHash("test", 4);
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectHashFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectHash myFoo = new IncorrectHash("test", 4);
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Test
  public void testCorrectDeepEqualsAndHashPassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    ClassWithCorrectEqualsMember myFoo = new ClassWithCorrectEqualsMember("test", "inner test");
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testDeepIncorrectEqualsFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    ClassWithIncorrectEqualsMember myFoo =
        new ClassWithIncorrectEqualsMember("test", "inner test", 4);
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectEqualsInSuperclassFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    SubIncorrectEqualsAndHash myFoo = new SubIncorrectEqualsAndHash("test", 4, new Long(39903));
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Test
  public void testCorrectEqualsAndHashInArrayPassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    CorrectEqualsAndHash myFoo = new CorrectEqualsAndHash("test");
    CorrectEqualsAndHash[] array = new CorrectEqualsAndHash[1];
    array[0] = myFoo;
    DeepEqualsTester.doDeepEqualsAndHashTest(array, testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectEqualsInArrayFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectEqualsAndHash myFoo = new IncorrectEqualsAndHash("test", 4);
    IncorrectEqualsAndHash[] array = new IncorrectEqualsAndHash[1];
    array[0] = myFoo;
    IncorrectEqualsWithArray toTest = new IncorrectEqualsWithArray(array);
    DeepEqualsTester.doDeepEqualsAndHashTest(toTest, testExamples);
  }

  @Test
  public void testClassWithSetWithCorrectDeepEqualsAndHashPassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    Set<File> myFiles = Sets.newHashSet();
    myFiles.add(new File("foo"));
    myFiles.add(new File("bar"));
    SimpleClassWithSet myFoo = new SimpleClassWithSet(myFiles);
    DeepEqualsTester.doDeepEqualsAndHashTest(myFoo, testExamples);
  }

  @Ignore("causes java reflection return a type variable instead of a concrete type")
  @Test
  public void testCorrectEqualsAndHashInSetPassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    CorrectEqualsAndHash myFoo = new CorrectEqualsAndHash("test");
    HashSet<CorrectEqualsAndHash> set = Sets.newHashSet();
    set.add(myFoo);
    DeepEqualsTester.doDeepEqualsAndHashTest(
        new Box<HashSet<CorrectEqualsAndHash>>(set), testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectEqualsInSetFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectEqualsAndHash myFoo = new IncorrectEqualsAndHash("test", 4);
    HashSet<IncorrectEqualsAndHash> set = Sets.newHashSet();
    set.add(myFoo);
    DeepEqualsTester.doDeepEqualsAndHashTest(
        new Box<HashSet<IncorrectEqualsAndHash>>(set), testExamples);
  }

  @Ignore("causes java reflection return a type variable instead of a concrete type")
  @Test
  public void testCorrectDeepEqualsAndHashInSetPassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    CorrectEqualsAndHash myFoo = new CorrectEqualsAndHash("test");
    DeepClass<CorrectEqualsAndHash> data =
        new DeepClass<CorrectEqualsAndHash>(ENUMS.THREE, 'z', myFoo, new File("home"));
    HashSet<DeepClass<CorrectEqualsAndHash>> set = Sets.newHashSet();
    set.add(data);
    DeepEqualsTester.doDeepEqualsAndHashTest(
        new Box<HashSet<DeepClass<CorrectEqualsAndHash>>>(set), testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectDeepEqualsInSetFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectEqualsAndHash myFoo = new IncorrectEqualsAndHash("test", 4);
    DeepClass<IncorrectEqualsAndHash> data =
        new DeepClass<IncorrectEqualsAndHash>(ENUMS.ONE, 'e', myFoo, new File("home"));
    HashSet<DeepClass<IncorrectEqualsAndHash>> set = Sets.newHashSet();
    set.add(data);
    DeepEqualsTester.doDeepEqualsAndHashTest(
        new Box<HashSet<DeepClass<IncorrectEqualsAndHash>>>(set), testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectEqualsForMapKeyFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectEqualsAndHash myFoo = new IncorrectEqualsAndHash("test", 4);
    Map<IncorrectEqualsAndHash, File> map = Maps.newHashMap();
    map.put(myFoo, new File("file"));
    MapWithIncorrectKey data = new MapWithIncorrectKey(map);
    DeepEqualsTester.doDeepEqualsAndHashTest(data, testExamples);
  }

  @Test(expected = AssertionError.class)
  public void testIncorrectEqualsForMapValueFailsTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    IncorrectEqualsAndHash myFoo = new IncorrectEqualsAndHash("test", 4);
    Map<String, IncorrectEqualsAndHash> map = Maps.newHashMap();
    map.put("first", myFoo);
    MapWithIncorrectValue data = new MapWithIncorrectValue(map);
    DeepEqualsTester.doDeepEqualsAndHashTest(data, testExamples);
  }

  @Test
  public void testCorrectEqualsAndHashForMapKeyValuePassesTester()
      throws IllegalAccessException, InstantiationException, NoSuchFieldException,
          ExampleNotFoundException, TestCorrectnessException {
    Map<String, String> map = Maps.newHashMap();
    map.put("key", "value");
    MapWithCorrectKeyAndValues data = new MapWithCorrectKeyAndValues(map);
    DeepEqualsTester.doDeepEqualsAndHashTest(data, testExamples);
  }

  @Test
  public void testEqualsAfterCloneReturnsTrue() {
    CorrectEqualsAndHash myData = new CorrectEqualsAndHash("my data");
    CorrectEqualsAndHash clone =
        (CorrectEqualsAndHash) DeepEqualsTesterUtil.cloneWithSerialization(myData);
    Assert.assertEquals(myData, clone);
  }
}
