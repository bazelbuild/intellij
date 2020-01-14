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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

    File tempOutput = File.createTempFile("repackaged_output_jar", ".jar");

    ShadeRequest shadeRequest = new ShadeRequest();
    shadeRequest.setJars(ImmutableSet.of(input));
    shadeRequest.setUberJar(tempOutput);
    shadeRequest.setRelocators(loadRelocators());
    shadeRequest.setFilters(ImmutableList.of());
    shadeRequest.setResourceTransformers(ImmutableList.of(new ServicesResourceTransformer()));

    getShader().shade(shadeRequest);

    copyJarWhileSettingtConstTimestamps(tempOutput, output);
    tempOutput.delete();
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

  /**
   * The the last modification time -- {@link ZipEntry#getTime}) that simulates the time used by
   * Blaze.
   *
   * <p>The use of the {@link ZoneOffset#systemDefault()} is on purpose (even if it might look
   * strange). This is because ZIP seem to use MS-DOS/FAT last-modified-time format
   * (https://en.wikipedia.org/wiki/Zip_(file_format)), which leads to timestamps being
   * time-zoneless, and resolution being 2 seconds (the rounding of seconds seem to vary between
   * tools). Since the {@link java.util.TimeZone#getDefault() default TimeZone} will be used to
   * convert the {@code ALL_ENTRIES_MODIFIED_TIME} to the MS-DOS date and time, according to {@link
   * ZipEntry#setTime}, the resulting datetime of the entries/files in the ZIP/jar file will be
   * time-zoneless `2010-01-01 00:00:00` (+/- 2 seconds potentially as viewed by some tools).
   *
   * <p>There are extra fields in ZIP format that allow to set date-times that accurately define
   * "time instances" -- {@link ZipEntry#setCreationTime}, {@link ZipEntry#setLastAccessTime},
   * {@link ZipEntry#setLastModifiedTime} (this one sets both extra and DOS timestamp fields), but
   * blaze seem to not set those.
   */
  private static final long ALL_ENTRIES_MODIFIED_TIME =
      ZonedDateTime.of(2010, 1, 1, 0, 0, 0, 0, ZoneOffset.systemDefault())
          .toInstant()
          .toEpochMilli();

  /**
   * Copies input jar to output jar with setting the last modification time ({@link
   * ZipEntry#setTime}) of the all entries/files to the constant {@link #ALL_ENTRIES_MODIFIED_TIME}
   * in the output jar.
   *
   * @param inputFile input jar file
   * @param outputFile output jar file
   */
  private static void copyJarWhileSettingtConstTimestamps(File inputFile, File outputFile)
      throws IOException {
    try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(outputFile));
        ZipInputStream input = new ZipInputStream(new FileInputStream(inputFile))) {
      byte[] buffer = new byte[8 * 1024];
      for (ZipEntry e = input.getNextEntry(); e != null; e = input.getNextEntry()) {
        e.setTime(ALL_ENTRIES_MODIFIED_TIME);
        output.putNextEntry(e);

        int n;
        while ((n = input.read(buffer, 0, buffer.length)) > 0) {
          output.write(buffer, 0, n);
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    new Repackager().processJar(args);
  }
}
