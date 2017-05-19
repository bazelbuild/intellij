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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Utilities for deep equals testing. */
@VisibleForTesting
public final class DeepEqualsTesterUtil {
  public static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = Lists.newArrayList();

    Field[] declaredFields = clazz.getDeclaredFields();
    for (Field field : declaredFields) {
      fields.add(field);
    }

    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null) {
      fields.addAll(getAllFields(superclass));
    }
    return fields;
  }

  public static Class getClass(Class declaredClass, Object o) {
    if (o == null) {
      return declaredClass;
    }
    // The two classes *should* be the same in this case, but the class from o.getClass won't
    // return true from isPrimitive
    if (declaredClass.isPrimitive()) {
      return declaredClass;
    }
    return o.getClass();
  }

  /** Do a deep clone of an object using reflection */
  public static Object cloneWithSerialization(Object o) {
    if (o == null) {
      return null;
    }

    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ObjectOutputStream objOut = new ObjectOutputStream(outputStream);
      objOut.writeObject(o);
      ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
      ObjectInputStream objIn = new ObjectInputStream(inputStream);
      return objIn.readObject();
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isSubclassOf(
      @Nullable Class<?> possibleSubClass, @NotNull Class<?> possibleSuperClass) {
    if (possibleSubClass == null) {
      return false;
    }

    if (possibleSubClass.equals(possibleSuperClass)) {
      return true;
    }

    if (possibleSubClass.equals(Object.class)) {
      return false;
    }

    Class<?>[] interfaces = possibleSubClass.getInterfaces();
    for (Class<?> interfaze : interfaces) {
      if (interfaze.equals(possibleSuperClass)) {
        return true;
      }
    }

    return isSubclassOf(possibleSubClass.getSuperclass(), possibleSuperClass);
  }
}
