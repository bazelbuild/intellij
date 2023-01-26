/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import com.google.common.collect.Maps;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Summaries the output from a {@code query} invocation into just the data needed by the rest of
 * querysync.
 *
 * <p>The main purpose of the summarized output is to allow the outputs from multiple {@code query}
 * invocations to be combined. This enables delta updates to the project.
 *
 * <p>If extra data from the {@code query} invocation is needed by later stages of sync, that data
 * should be added to the {@link Query.Summary} proto and this code should be updated accordingly.
 * The proto should remain a simple mapping of data from the build proto, i.e. no complex
 * functionality should be added to this class. Non-trivial calculations based on the output of the
 * query belong in {@link com.google.idea.blaze.qsync.BlazeQueryParser} instead.
 *
 * <p>Instances of the the {@link Query.Summary} proto are maintained in memory so data should not
 * be added to it unnecessarily.
 */
public class QueryOutputSummarizer {

  private QueryOutputSummarizer() {}

  public static Query.Summary summarize(InputStream protoInputStream) throws IOException {
    Map<String, Query.SourceFile> sourceFileMap = Maps.newHashMap();
    Map<String, Query.Rule> ruleMap = Maps.newHashMap();
    Build.Target target;
    while ((target = Target.parseDelimitedFrom(protoInputStream)) != null) {
      switch (target.getType()) {
        case SOURCE_FILE:
          Query.SourceFile sourceFile =
              Query.SourceFile.newBuilder()
                  .setLocation(target.getSourceFile().getLocation())
                  .build();
          sourceFileMap.put(target.getSourceFile().getName(), sourceFile);
          break;
        case RULE:
          // TODO We don't need all rules types in the proto since many are not user later on.
          //   We could filter the rules here, or even create rule-specific proto messages to
          //   reduce the size of the output proto.
          Query.Rule.Builder rule =
              Query.Rule.newBuilder().setRuleClass(target.getRule().getRuleClass());
          for (Build.Attribute a : target.getRule().getAttributeList()) {
            if (a.getName().equals("srcs")) {
              rule.addAllSources(a.getStringListValueList());
            } else if (a.getName().equals("deps")) {
              rule.addAllDeps(a.getStringListValueList());
            } else if (a.getName().equals("idl_srcs")) {
              rule.addAllIdlSources(a.getStringListValueList());
            }
          }
          ruleMap.put(target.getRule().getName(), rule.build());
          break;
        default:
          break;
      }
    }
    return Query.Summary.newBuilder().putAllSourceFiles(sourceFileMap).putAllRules(ruleMap).build();
  }

  public static Query.Summary summarize(File protoFile) throws IOException {
    return summarize(new BufferedInputStream(new FileInputStream(protoFile)));
  }
}
