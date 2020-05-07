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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.idea.blaze.aspect.KotlinGenJarFilter.KotlinGenJarFilterOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link KotlinGenJarFilter} */
@RunWith(JUnit4.class)
public class KotlinGenJarFilterTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testFilterMethod() throws Exception {
    Set<String> prefixes =
        ImmutableSet.of("com/google/foo/Foo", "com/google/bar/Bar", "com/google/baz/Baz");

    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "com/google/foo/Foo.class")).isFalse();
    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "com/google/foo/Foo$Inner.class"))
        .isFalse();
    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "com/google/bar/Bar.class")).isFalse();

    assertThat(
            KotlinGenJarFilter.shouldKeepClass(
                prefixes, "META-INF/TRANSITIVE/com/google/foo/Foo.class"))
        .isFalse();
    assertThat(
            KotlinGenJarFilter.shouldKeepClass(
                prefixes, "META-INF/TRANSITIVE/com/google/foo/Foo.class"))
        .isFalse();
    assertThat(
            KotlinGenJarFilter.shouldKeepClass(
                prefixes, "META-INF/TRANSITIVE/com/google/foo/NotFoo.class"))
        .isFalse();

    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "com/google/foo/Foo/NotFoo.class"))
        .isTrue();
    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "wrong/com/google/foo/Foo.class"))
        .isTrue();

    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "com/google/foo/bar/Bar.class"))
        .isTrue();
    assertThat(KotlinGenJarFilter.shouldKeepClass(prefixes, "com/google/baz/NotBaz.class"))
        .isTrue();
  }

  @Test
  @SuppressWarnings("JdkObsolete")
  public void fullIntegrationTest() throws Exception {
    File fooJava = folder.newFile("Foo.java");
    Files.asCharSink(fooJava, UTF_8).write("package com.google.foo; class Foo { class Inner {} }");

    File barKt = folder.newFile("Bar.kt");
    Files.asCharSink(barKt, UTF_8).write("package com.google.foo.bar\nclass Bar {}");

    File filterJar = folder.newFile("foo.jar");
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(filterJar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/Foo.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("com/google/foo/Foo$Inner.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("com/google/foo/bar/Bar.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("gen/Gen.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("gen/Gen2.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("gen/Gen3.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("com/google/foo/Foo2.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("META-INF/TRANSITIVE/com/google/foo/Foo.class"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("META-INF/TRANSITIVE/com/google/foo/Foo2.class"));
      zo.closeEntry();
    }
    File filterSrcJar = folder.newFile("foo-src.jar");
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(filterSrcJar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/Foo.java"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("com/google/foo/bar/Bar.kt"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("gen/Gen.java"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("gen/Gen2.java"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("gen/Gen3.kt"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("com/google/foo/Foo2.java"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("META-INF/TRANSITIVE/com/google/foo/Foo.java"));
      zo.closeEntry();
      zo.putNextEntry(new ZipEntry("META-INF/TRANSITIVE/com/google/foo/Foo2.kt"));
      zo.closeEntry();
    }

    File filteredJar = folder.newFile("foo-kt-gen.jar");
    File filteredSourceJar = folder.newFile("foo-src-kt-gen.jar");

    String[] args =
        new String[] {
          "--jar",
          filterJar.getPath(),
          "--filtered_jar",
          filteredJar.getPath(),
          "--srcjar",
          filterSrcJar.getPath(),
          "--filtered_srcjar",
          filteredSourceJar.getPath(),
          "--sources",
          String.format("%s,%s", fooJava.getPath(), barKt.getPath())
        };
    KotlinGenJarFilterOptions options = KotlinGenJarFilter.parseArgs(args);
    KotlinGenJarFilter.main(options);

    List<String> filteredJarNames = Lists.newArrayList();
    try (ZipFile zipFile = new ZipFile(filteredJar)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        filteredJarNames.add(zipEntry.getName());
      }
    }

    List<String> filteredSourceJarNames = Lists.newArrayList();
    try (ZipFile zipFile = new ZipFile(filteredSourceJar)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        filteredSourceJarNames.add(zipEntry.getName());
      }
    }

    assertThat(filteredJarNames)
        .containsExactly(
            "gen/Gen.class", "gen/Gen2.class", "gen/Gen3.class", "com/google/foo/Foo2.class");

    assertThat(filteredSourceJarNames)
        .containsExactly(
            "gen/Gen.java", "gen/Gen2.java", "gen/Gen3.kt", "com/google/foo/Foo2.java");
  }
}
