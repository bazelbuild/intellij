/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect;

import static com.google.idea.blaze.aspect.OptionParser.parseParamFileIfUsed;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;

/**
 * Filters a JAR and removes all classes/sources corresponding to a given set of expected sources.
 */
public final class KotlinGenJarFilter {

  private KotlinGenJarFilter() {}

  /** Options for {@link KotlinGenJarFilter} */
  @VisibleForTesting
  static final class KotlinGenJarFilterOptions {
    Path jar;
    Path outJar;
    @Nullable Path srcJar;
    @Nullable Path outSrcJar;
    Set<Path> sourcePaths;
  }

  private static Path getPath(String path) {
    return FileSystems.getDefault().getPath(path);
  }

  private static Set<Path> filelistParser(String string) {
    return Arrays.stream(string.split(","))
        .map(pathString -> FileSystems.getDefault().getPath(pathString))
        .collect(Collectors.toSet());
  }

  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("^\\s*package\\s+([\\w\\.]+);?\\s*");
  private static final Logger logger = Logger.getLogger(KotlinGenJarFilter.class.getName());

  @VisibleForTesting
  static KotlinGenJarFilterOptions parseArgs(String[] args) {
    args = parseParamFileIfUsed(args);
    KotlinGenJarFilterOptions options = new KotlinGenJarFilterOptions();
    options.jar = OptionParser.parseSingleOption(args, "jar", KotlinGenJarFilter::getPath);
    options.outJar =
        OptionParser.parseSingleOption(args, "filtered_jar", KotlinGenJarFilter::getPath);
    options.srcJar = OptionParser.parseSingleOption(args, "srcjar", KotlinGenJarFilter::getPath);
    options.outSrcJar =
        OptionParser.parseSingleOption(args, "filtered_srcjar", KotlinGenJarFilter::getPath);
    options.sourcePaths =
        OptionParser.parseSingleOption(args, "sources", KotlinGenJarFilter::filelistParser);

    return options;
  }

  public static void main(String[] args) {
    KotlinGenJarFilterOptions options = parseArgs(args);
    try {
      main(options);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error creating pseudo gen-jars", e);
      System.exit(1);
    }
    System.exit(0);
  }

  static void main(KotlinGenJarFilterOptions options) throws IOException {
    Preconditions.checkNotNull(options.jar);
    Preconditions.checkNotNull(options.outJar);
    Preconditions.checkNotNull(options.sourcePaths);
    Preconditions.checkState(
        (options.srcJar == null && options.outSrcJar == null)
            || (options.srcJar != null && options.outSrcJar != null));

    Set<String> expectedSourcePaths = parseSourceFiles(options.sourcePaths);

    filterJars(options.jar, options.outJar, path -> shouldKeepClass(expectedSourcePaths, path));

    if (options.srcJar != null) {
      filterJars(
          options.srcJar, options.outSrcJar, path -> shouldKeepSource(expectedSourcePaths, path));
    }
  }

  /** Filters a list of jars, keeping anything matching the passed predicate. */
  @SuppressWarnings("JdkObsolete")
  private static void filterJars(Path jar, Path outJar, Predicate<String> shouldKeep)
      throws IOException {
    final int bufferSize = 8 * 1024;
    byte[] buffer = new byte[bufferSize];
    Set<String> names = new HashSet<>();

    try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(outJar.toFile()));
        ZipFile sourceZipFile = new ZipFile(jar.toFile())) {
      Enumeration<? extends ZipEntry> entries = sourceZipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (!shouldKeep.test(entry.getName())) {
          continue;
        }
        if (!names.add(entry.getName())) {
          // ignore duplicate entries, on the assumption that their contents are identical
          continue;
        }

        ZipEntry newEntry = new ZipEntry(entry.getName());
        outputStream.putNextEntry(newEntry);
        try (InputStream inputStream = sourceZipFile.getInputStream(entry)) {
          int len;
          while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
          }
        }
      }
    }
  }

  @VisibleForTesting
  static boolean shouldKeepClass(Set<String> expectedSourcePaths, String path) {
    // Keep any file that does not end with `.class`
    if (!path.endsWith(".class")) {
      return true;
    }

    // Remove any class file in META-INF
    // We do not want class files in META-INF/ to be read and indexed by the IDE
    if (path.startsWith("META-INF")) {
      return false;
    }

    boolean isInSource =
        expectedSourcePaths.stream()
            .filter(path::startsWith)
            .filter(expectedPath -> path.length() > expectedPath.length())
            .map(expectedPath -> path.charAt(expectedPath.length()))
            .anyMatch(c -> c == '.' || c == '$');

    // Do not keep if we already have source for it
    return !isInSource;
  }

  private static boolean shouldKeepSource(Set<String> expectedSourcePaths, String path) {
    // Keep any file that does not end with `.java` or `.kt`
    if (!path.endsWith(".java") && !path.endsWith(".kt")) {
      return true;
    }

    // Remove any source file in META-INF
    // We do not want source files in META-INF/ since their corresponding `.class` file would be
    // removed as well
    if (path.startsWith("META-INF")) {
      return false;
    }

    int lastDotIndex = path.lastIndexOf('.');
    String nameWithoutExt = lastDotIndex != -1 ? path.substring(0, lastDotIndex) : path;

    // Do not keep if we already have source for it
    return !expectedSourcePaths.contains(nameWithoutExt);
  }

  /** Finds the expected jar archive file name prefixes for the java/kotlin files. */
  private static Set<String> parseSourceFiles(Set<Path> sourceFiles) throws IOException {
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

    List<ListenableFuture<String>> futures = Lists.newArrayList();
    for (final Path sourceFile : sourceFiles) {
      futures.add(
          executorService.submit(
              () -> {
                String packageString = getDeclaredPackageOfJavaFile(sourceFile);
                return packageString != null
                    ? getArchiveFileNamePrefix(sourceFile.toString(), packageString)
                    : null;
              }));
    }
    try {
      List<String> archiveFileNamePrefixes = Futures.allAsList(futures).get();
      Set<String> result = new HashSet<>();
      for (String archiveFileNamePrefix : archiveFileNamePrefixes) {
        if (archiveFileNamePrefix != null) {
          result.add(archiveFileNamePrefix);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  /**
   * Computes the expected archive file name prefix of a java class.
   *
   * <p>Eg.: file java/com/google/foo/Foo.java, package com.google.foo -> com/google/foo/Foo
   */
  private static String getArchiveFileNamePrefix(String filePath, String packageString) {
    int lastSlashIndex = filePath.lastIndexOf('/');
    // On Windows, the separator could be '\\'
    if (lastSlashIndex == -1) {
      lastSlashIndex = filePath.lastIndexOf('\\');
    }
    String fileName = lastSlashIndex != -1 ? filePath.substring(lastSlashIndex + 1) : filePath;

    int lastDotIndex = fileName.lastIndexOf('.');
    String className = lastDotIndex != -1 ? fileName.substring(0, lastDotIndex) : fileName;
    return packageString.replace('.', '/') + '/' + className;
  }

  private static String getDeclaredPackageOfJavaFile(Path sourceFile) {
    try (BufferedReader reader = java.nio.file.Files.newBufferedReader(sourceFile, UTF_8)) {
      return parseDeclaredPackage(reader);

    } catch (IOException e) {
      logger.log(Level.WARNING, "Error parsing package string from source: " + sourceFile, e);
      return null;
    }
  }

  @Nullable
  private static String parseDeclaredPackage(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      Matcher packageMatch = PACKAGE_PATTERN.matcher(line);
      if (packageMatch.find()) {
        return packageMatch.group(1);
      }
    }
    return null;
  }
}
