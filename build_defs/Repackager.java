/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.shade.DefaultShader;
import org.apache.maven.plugins.shade.ShadeRequest;
import org.apache.maven.plugins.shade.Shader;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.relocation.SimpleRelocator;
import org.apache.maven.plugins.shade.resource.ServicesResourceTransformer;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

final class Repackager {

  @Option(name = "--input")
  private File input;

  @Option(name = "--output")
  private File output;

  @Option(name = "--rules")
  private File rules;

  private void processJar(String[] args)
      throws PlexusContainerException, ComponentLookupException, IOException,
          MojoExecutionException {

    if (!checkArguments(args)) {
      System.exit(1);
    }

    ShadeRequest shadeRequest = new ShadeRequest();
    shadeRequest.setJars(ImmutableSet.of(input));
    shadeRequest.setUberJar(output);
    shadeRequest.setRelocators(loadRelocators());
    shadeRequest.setFilters(ImmutableList.of());
    shadeRequest.setResourceTransformers(ImmutableList.of(new ServicesResourceTransformer()));

    getShader().shade(shadeRequest);
  }

  private Shader getShader() throws PlexusContainerException, ComponentLookupException {
    DefaultPlexusContainer container = new DefaultPlexusContainer();
    ClassRealm classRealm = container.getClassWorld().getClassRealm("plexus.core");
    ComponentDescriptor<DefaultShader> shaderDescriptor =
        new ComponentDescriptor<>(DefaultShader.class, classRealm);
    shaderDescriptor.setRole(Shader.class.getName());
    container.addComponentDescriptor(shaderDescriptor);
    return container.lookup(Shader.class);
  }

  private boolean checkArguments(String[] args) {

    CmdLineParser parser = new CmdLineParser(this);

    boolean errors = false;
    try {
      parser.parseArgument(args);
      if (input == null) {
        errors = true;
        System.err.println("--input is required");
      }
      if (output == null) {
        errors = true;
        System.err.println("--output is required");
      }
      if (rules == null) {
        errors = true;
        System.err.println("--rules is required");
      }
    } catch (CmdLineException e) {
      errors = true;
      System.err.println("Error: " + e.getMessage());
    }

    if (errors) {
      parser.printUsage(System.err);
      return false;
    }
    return true;
  }

  private ImmutableList<Relocator> loadRelocators() throws IOException {

    ImmutableList.Builder<Relocator> relocators = ImmutableList.builder();

    int lineNum = 0;
    for (String line : Files.asCharSource(rules, StandardCharsets.UTF_8).readLines()) {
      ++lineNum;
      List<String> splits = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(line);
      if (splits.isEmpty()) {
        continue;
      }
      checkArgument(splits.size() == 2, "Line %s in %s is invalid: %s", lineNum, rules, line);
      relocators.add(
          new SimpleRelocator(
              splits.get(0),
              splits.get(1),
              /* includes= */ ImmutableList.of(),
              /* excludes= */ ImmutableList.of()));
    }

    return relocators.build();
  }

  public static void main(String[] args) throws Exception {
    new Repackager().processJar(args);
  }
}
