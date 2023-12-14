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
package com.google.idea.blaze.qsync.java;

import static java.util.function.Predicate.not;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Closer;
import com.google.idea.blaze.qsync.artifacts.BuildArtifactProvider;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Updates the project proto with the android resources packages extracted by the aspect in a
 * dependencies build.
 */
public class AndroidResPackagesProjectUpdater {

  private final Logger logger =
      Logger.getLogger(AndroidResPackagesProjectUpdater.class.getSimpleName());

  private final Project project;
  private final ImmutableList<JavaArtifactInfo> javaArtifacts;
  private final BuildArtifactProvider artifactProvider;
  private final ProjectPath.Resolver pathResolver;

  public AndroidResPackagesProjectUpdater(
      Project project,
      Iterable<JavaArtifactInfo> javaArtifacts,
      BuildArtifactProvider artifactProvider,
      ProjectPath.Resolver pathResolver) {
    this.project = project;
    this.javaArtifacts = ImmutableList.copyOf(javaArtifacts);
    this.artifactProvider = artifactProvider;
    this.pathResolver = pathResolver;
  }

  /**
   * Resolves a path from a Java target info proto message into an absolute path.
   *
   * @param relative The relative path. This may refer to a build artifact, in which case it will
   *     start with {@code (blaze|bazel)-out}, or a workspace relative source path.
   * @return An absolute path for the source file or build artifact.
   */
  private Optional<Path> resolveManifestPath(Path relative) {
    if (relative.getName(0).toString().endsWith("-out")) {
      return artifactProvider.getCachedArtifact(relative.subpath(1, relative.getNameCount()));
    } else {
      return Optional.of(pathResolver.resolve(ProjectPath.workspaceRelative(relative)));
    }
  }

  private String readPackageFromManifest(Path manifestPath) {
    return resolveManifestPath(manifestPath).map(this::parseProjectFromXmlFile).orElse("");
  }

  private String parseProjectFromXmlFile(Path absolutePath) {
    XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    try (Closer closer = Closer.create()) {
      XMLEventReader reader =
          xmlInputFactory.createXMLEventReader(Files.newInputStream(absolutePath));
      closer.register(
          () -> {
            try {
              reader.close();
            } catch (XMLStreamException e) {
              throw new IOException(e);
            }
          });
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isStartElement()) {
          StartElement startElement = event.asStartElement();
          if (startElement.getName().getLocalPart().equals("manifest")) {
            Attribute pname = startElement.getAttributeByName(new QName("package"));
            if (pname == null) {
              return "";
            }
            return pname.getValue();
          }
        }
      }
    } catch (IOException | XMLStreamException e) {
      logger.log(Level.WARNING, "Failed to read package name from " + absolutePath, e);
    }
    return "";
  }

  public Project addAndroidResPackages() {
    ImmutableList<String> packages =
        javaArtifacts.stream()
            .map(JavaArtifactInfo::androidManifestFile)
            .filter(p -> !p.toString().isEmpty())
            .map(this::readPackageFromManifest)
            .filter(not(Strings::isNullOrEmpty))
            .distinct()
            .collect(ImmutableList.toImmutableList());
    if (packages.isEmpty()) {
      return project;
    }
    return project.toBuilder()
        .setModules(
            0,
            Iterables.getOnlyElement(project.getModulesList()).toBuilder()
                .addAllAndroidSourcePackages(packages)
                .build())
        .build();
  }
}
