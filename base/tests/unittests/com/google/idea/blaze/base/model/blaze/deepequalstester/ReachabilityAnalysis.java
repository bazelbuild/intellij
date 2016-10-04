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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Assert;

final class ReachabilityAnalysis {

  /** Wrapper around a map from class to set of paths that lead to that path from the root object */
  public static final class ReachableClasses {

    Map<Class<? extends Serializable>, Set<List<String>>> map;

    public ReachableClasses() {
      map = Maps.newHashMap();
    }

    boolean alreadyFound(Class<? extends Serializable> clazz) {
      return map.containsKey(clazz);
    }

    void addPath(Class<? extends Serializable> clazz, List<String> path) {
      Set<List<String>> paths;
      if (map.containsKey(clazz)) {
        paths = map.get(clazz);
      } else {
        paths = Sets.newHashSet();
        map.put(clazz, paths);
      }
      paths.add(path);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      Set<? extends Entry<Class<? extends Serializable>, ? extends Set<? extends List<String>>>>
          entries = map.entrySet();
      for (Entry<Class<? extends Serializable>, ? extends Set<? extends List<String>>> entry :
          entries) {
        sb.append(entry.getKey().toString());
        sb.append("\n");
      }

      return sb.toString();
    }

    public Set<Class<? extends Serializable>> getClasses() {
      return map.keySet();
    }

    public List<String> getExamplePathTo(Class<? extends Serializable> aClass) {
      if (map.containsKey(aClass)) {
        return map.get(aClass).iterator().next();
      }
      return Lists.newArrayList();
    }
  }

  /**
   * Find all of the classes reachable from a root object
   *
   * @param root object to start reachability calculation from
   * @param declaredRootClass declared class of the root object
   * @param currentPath field access path to get to root
   * @param reachableClasses output: add classes reachable from root to this object
   * @throws IllegalAccessException
   * @throws ClassNotFoundException
   */
  public static void computeReachableFromObject(
      Object root,
      Class<?> declaredRootClass,
      List<String> currentPath,
      ReachableClasses reachableClasses)
      throws IllegalAccessException, ClassNotFoundException {
    final Class<?> concreteRootClass = DeepEqualsTesterUtil.getClass(declaredRootClass, root);
    List<Field> allFields = DeepEqualsTesterUtil.getAllFields(concreteRootClass);
    for (Field field : allFields) {
      if (!Modifier.isStatic(field.getModifiers())) {
        field.setAccessible(true);
        final Object fieldObject;
        if (root == null) {
          fieldObject = null;
        } else {
          fieldObject = field.get(root);
        }
        List<String> childPath = Lists.newArrayList();
        childPath.addAll(currentPath);
        childPath.add(field.toString());
        addToReachableAndRecurse(
            fieldObject, field.getType(), field.getGenericType(), childPath, reachableClasses);
      }
    }
  }

  /**
   * Determine the action we should take based on the type of Object and then take it. In the normal
   * object case, this results in a recursive call to {@link #computeReachableFromObject(Object,
   * Class, List, ReachableClasses)}. In the case of Collections, we skip the Collection type and
   * continue on with the type contained in the collection.
   */
  private static void addToReachableAndRecurse(
      Object object,
      Class<?> declaredObjectClass,
      Type genericType,
      List<String> currentPath,
      ReachableClasses reachableClasses)
      throws ClassNotFoundException, IllegalAccessException {
    Class<? extends Serializable> objectType =
        DeepEqualsTesterUtil.getClass(declaredObjectClass, object);
    // TODO(salguarnieri) modify if so all ignored classes are taken care of together
    if (objectType.isPrimitive()) {
      // ignore
    } else if (objectType.isEnum()) {
      // assume enums do the right thing, ignore
    } else if (DeepEqualsTesterUtil.isSubclassOf(objectType, String.class)) {
      // ignore
    } else if (DeepEqualsTesterUtil.isSubclassOf(objectType, File.class)) {
      // ignore
    } else if (DeepEqualsTesterUtil.isSubclassOf(objectType, Collection.class)
        || DeepEqualsTesterUtil.isSubclassOf(objectType, Map.class)) {
      if (genericType instanceof ParameterizedType) {
        ParameterizedType parameterType = (ParameterizedType) genericType;
        Type[] actualTypeArguments = parameterType.getActualTypeArguments();
        for (Type typeArgument : actualTypeArguments) {
          if (typeArgument instanceof Class) {
            List<String> childPath = Lists.newArrayList();
            childPath.addAll(currentPath);
            childPath.add("[[IN COLLECTION]]");
            // this does not properly handle subtyping
            addToReachableAndRecurse(null, (Class) typeArgument, null, childPath, reachableClasses);
          } else {
            Assert.fail("This case is not handled yet");
          }
        }
      } else {
        Assert.fail("This case is not handled yet");
      }
    } else if (objectType.isArray()) {
      Class<?> typeInArray = objectType.getComponentType();
      // This does not properly handle subtyping
      List<String> childPath = Lists.newArrayList();
      childPath.addAll(currentPath);
      childPath.add("[[IN ARRAY]]");
      addToReachableAndRecurse(null, typeInArray, null, childPath, reachableClasses);
    } else {
      boolean doRecursion = !reachableClasses.alreadyFound(objectType);
      reachableClasses.addPath(objectType, currentPath);
      if (doRecursion) {
        computeReachableFromObject(object, declaredObjectClass, currentPath, reachableClasses);
      }
    }
  }
}
