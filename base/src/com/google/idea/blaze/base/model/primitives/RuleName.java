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

/** The rule name part of a label */
public final class RuleName {

  // This is a subset of the allowable target names in Blaze
  private static final String ALNUM_REGEX_STR = "[a-zA-Z0-9]*";
  private static final Pattern ALNUM_REGEX = Pattern.compile(ALNUM_REGEX_STR);

  // Rule names must be alpha-numeric or consist of the following allowed chars:
  // (note, rule names can also contain '/'; we handle that case separately)
  private static final ImmutableSet<Character> ALLOWED_META =
      ImmutableSet.of('+', '_', ',', '=', '-', '.', '@', '~');

  private final String name;

  private RuleName(String ruleName) {
    this.name = ruleName;
  }

  /** Silently returns null if the string is not a valid rule name. */
  @Nullable
  public static RuleName createIfValid(String ruleName) {
    if (validate(ruleName, null)) {
      return new RuleName(ruleName);
    }
    return null;
  }

  public static RuleName create(String ruleName) {
    List<BlazeValidationError> errors = Lists.newArrayList();
    if (!validate(ruleName, errors)) {
      BlazeValidationError.throwError(errors);
    }
    return new RuleName(ruleName);
  }

  /** Validates a rule name using the same logic as Blaze */
  public static boolean validate(String ruleName) {
    return validate(ruleName, null);
  }

  /** Validates a rule name using the same logic as Blaze */
  public static boolean validate(
      String ruleName, @Nullable Collection<BlazeValidationError> errors) {
    if (ruleName.isEmpty()) {
      BlazeValidationError.collect(
          errors, new BlazeValidationError("target names cannot be empty"));
      return false;
    }
    // Forbidden start chars:
    if (ruleName.charAt(0) == '/') {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid target name: " + ruleName + "\n" + "target names may not start with \"/\""));
      return false;
    } else if (ruleName.charAt(0) == '.') {
      if (ruleName.startsWith("../") || ruleName.equals("..")) {
        BlazeValidationError.collect(
            errors,
            new BlazeValidationError(
                "Invalid target name: "
                    + ruleName
                    + "\n"
                    + "target names may not contain up-level references \"..\""));
        return false;
      } else if (ruleName.equals(".")) {
        return true;
      } else if (ruleName.startsWith("./")) {
        BlazeValidationError.collect(
            errors,
            new BlazeValidationError(
                "Invalid target name: "
                    + ruleName
                    + "\n"
                    + "target names may not contain \".\" as a path segment"));
        return false;
      }
    }

    for (int i = 0; i < ruleName.length(); ++i) {
      char c = ruleName.charAt(i);
      if (ALLOWED_META.contains(c)) {
        continue;
      }
      if (c == '/') {
        // Forbidden substrings: "/../", "/./", "//"
        if (ruleName.contains("/../")) {
          BlazeValidationError.collect(
              errors,
              new BlazeValidationError(
                  "Invalid target name: "
                      + ruleName
                      + "\n"
                      + "target names may not contain up-level references \"..\""));
          return false;
        } else if (ruleName.contains("/./")) {
          BlazeValidationError.collect(
              errors,
              new BlazeValidationError(
                  "Invalid target name: "
                      + ruleName
                      + "\n"
                      + "target names may not contain \".\" as a path segment"));
          return false;
        } else if (ruleName.contains("//")) {
          BlazeValidationError.collect(
              errors,
              new BlazeValidationError(
                  "Invalid target name: "
                      + ruleName
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
                "Invalid target name: " + ruleName + "\n" + "target names may not contain " + c));
        return false;
      }
    }

    // Forbidden end chars:
    if (ruleName.endsWith("/..")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid target name: "
                  + ruleName
                  + "\n"
                  + "target names may not contain up-level references \"..\""));
      return false;
    } else if (ruleName.endsWith("/.")) {
      return true;
    } else if (ruleName.endsWith("/")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid target name: " + ruleName + "\n" + "target names may not end with \"/\""));
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
    if (obj instanceof RuleName) {
      RuleName that = (RuleName) obj;
      return Objects.equal(name, that.name);
    }
    return false;
  }
}
