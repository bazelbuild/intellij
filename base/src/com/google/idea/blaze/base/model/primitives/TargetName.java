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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** The target name part of a label */
public final class TargetName {

  // This is a subset of the allowable target names in Blaze
  private static final String ALNUM_REGEX_STR = "[a-zA-Z0-9]*";
  private static final Pattern ALNUM_REGEX = Pattern.compile(ALNUM_REGEX_STR);

  // Rule names must be alpha-numeric or consist of the following allowed chars:
  // (note, rule names can also contain '/'; we handle that case separately)
  private static final ImmutableSet<Character> ALLOWED_META =
      ImmutableSet.of('+', '_', ',', '=', '-', '.', '@', '~');

  private final String name;

  private TargetName(String ruleName) {
    this.name = ruleName;
  }

  /** Silently returns null if the string is not a valid target name. */
  @Nullable
  public static TargetName createIfValid(String targetName) {
    if (validate(targetName, null)) {
      return new TargetName(targetName);
    }
    return null;
  }

  public static TargetName create(String targetName) {
    List<BlazeValidationError> errors = Lists.newArrayList();
    if (!validate(targetName, errors)) {
      BlazeValidationError.throwError(errors);
    }
    return new TargetName(targetName);
  }

  /** Validates a rule name using the same logic as Blaze */
  public static boolean validate(String targetName) {
    return validate(targetName, null);
  }

  /** Validates a rule name using the same logic as Blaze */
  public static boolean validate(
      String targetName, @Nullable Collection<BlazeValidationError> errors) {
    if (targetName.isEmpty()) {
      BlazeValidationError.collect(
          errors, new BlazeValidationError("target names cannot be empty"));
      return false;
    }
    // Forbidden start chars:
    if (targetName.charAt(0) == '/') {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid target name: "
                  + targetName
                  + "\n"
                  + "target names may not start with \"/\""));
      return false;
    } else if (targetName.charAt(0) == '.') {
      if (targetName.startsWith("../") || targetName.equals("..")) {
        BlazeValidationError.collect(
            errors,
            new BlazeValidationError(
                "Invalid target name: "
                    + targetName
                    + "\n"
                    + "target names may not contain up-level references \"..\""));
        return false;
      } else if (targetName.equals(".")) {
        return true;
      } else if (targetName.startsWith("./")) {
        BlazeValidationError.collect(
            errors,
            new BlazeValidationError(
                "Invalid target name: "
                    + targetName
                    + "\n"
                    + "target names may not contain \".\" as a path segment"));
        return false;
      }
    }

    for (int i = 0; i < targetName.length(); ++i) {
      char c = targetName.charAt(i);
      if (ALLOWED_META.contains(c)) {
        continue;
      }
      if (c == '/') {
        // Forbidden substrings: "/../", "/./", "//"
        if (targetName.contains("/../")) {
          BlazeValidationError.collect(
              errors,
              new BlazeValidationError(
                  "Invalid target name: "
                      + targetName
                      + "\n"
                      + "target names may not contain up-level references \"..\""));
          return false;
        } else if (targetName.contains("/./")) {
          BlazeValidationError.collect(
              errors,
              new BlazeValidationError(
                  "Invalid target name: "
                      + targetName
                      + "\n"
                      + "target names may not contain \".\" as a path segment"));
          return false;
        } else if (targetName.contains("//")) {
          BlazeValidationError.collect(
              errors,
              new BlazeValidationError(
                  "Invalid target name: "
                      + targetName
                      + "\n"
                      + "target names may not contain \"//\" path separators"));
          return false;
        }
        continue;
      }
      boolean isAlnum = ALNUM_REGEX.matcher(String.valueOf(c)).matches();
      if (!isAlnum) {
        BlazeValidationError.collect(
            errors,
            new BlazeValidationError(
                "Invalid target name: " + targetName + "\n" + "target names may not contain " + c));
        return false;
      }
    }

    // Forbidden end chars:
    if (targetName.endsWith("/..")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid target name: "
                  + targetName
                  + "\n"
                  + "target names may not contain up-level references \"..\""));
      return false;
    } else if (targetName.endsWith("/.")) {
      return true;
    } else if (targetName.endsWith("/")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid target name: " + targetName + "\n" + "target names may not end with \"/\""));
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof TargetName) {
      TargetName that = (TargetName) obj;
      return Objects.equal(name, that.name);
    }
    return false;
  }
}
