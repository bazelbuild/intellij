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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.common.guava.GuavaHelper;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/** Simple implementation of RuleDefinition, from build.proto */
public class RuleDefinition implements Serializable {

  private static final long serialVersionUID = 2L;

  /**
   * In previous versions of blaze/bazel, this wasn't included in the proto. All other documented
   * attributes seem to be.
   */
  private static final AttributeDefinition NAME_ATTRIBUTE =
      new AttributeDefinition("name", Build.Attribute.Discriminator.STRING, true, null, null);

  public static RuleDefinition fromProto(Build.RuleDefinition rule) {
    boolean hasNameAttr = false;
    Map<String, AttributeDefinition> map = new HashMap<>();
    for (Build.AttributeDefinition attr : rule.getAttributeList()) {
      map.put(attr.getName(), AttributeDefinition.fromProto(attr));
      hasNameAttr |= "name".equals(attr.getName());
    }
    if (!hasNameAttr) {
      map.put(NAME_ATTRIBUTE.name, NAME_ATTRIBUTE);
    }
    ImmutableMap<String, AttributeDefinition> sortedMap =
        map.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(GuavaHelper.toImmutableMap(Entry::getKey, Entry::getValue));
    return new RuleDefinition(
        rule.getName(), sortedMap, rule.hasDocumentation() ? rule.getDocumentation() : null);
  }

  public final String name;
  /** This map is not exhaustive; it only contains documented attributes. */
  public final ImmutableMap<String, AttributeDefinition> attributes;

  public final ImmutableMap<String, AttributeDefinition> mandatoryAttributes;

  @Nullable public final String documentation;

  public RuleDefinition(
      String name,
      ImmutableMap<String, AttributeDefinition> attributes,
      @Nullable String documentation) {
    this.name = name;
    this.attributes = attributes;
    this.documentation = documentation;

    ImmutableMap.Builder<String, AttributeDefinition> builder = ImmutableMap.builder();
    for (AttributeDefinition attr : attributes.values()) {
      if (attr.mandatory) {
        builder.put(attr.name, attr);
      }
    }
    mandatoryAttributes = builder.build();
  }

  public ImmutableSet<String> getKnownAttributeNames() {
    return attributes.keySet();
  }

  @Nullable
  public AttributeDefinition getAttribute(@Nullable String attributeName) {
    return attributeName != null ? attributes.get(attributeName) : null;
  }
}
