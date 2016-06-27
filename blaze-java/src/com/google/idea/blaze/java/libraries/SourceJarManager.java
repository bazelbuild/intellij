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
package com.google.idea.blaze.java.libraries;

import com.google.common.collect.Sets;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;

import java.util.Set;

/**
 * Keeps track of which libraries have source jars attached.
 */
@State(name = "BlazeSourceJarManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class SourceJarManager implements PersistentStateComponent<Element> {
  private Set<LibraryKey> librariesWithSourceJarsAttached = Sets.newHashSet();

  public static SourceJarManager getInstance(Project project) {
    return ServiceManager.getService(project, SourceJarManager.class);
  }

  public boolean hasSourceJarAttached(LibraryKey libraryKey) {
    return librariesWithSourceJarsAttached.contains(libraryKey);
  }

  public void setHasSourceJarAttached(LibraryKey libraryKey, boolean hasSourceJar) {
    if (hasSourceJar) {
      librariesWithSourceJarsAttached.add(libraryKey);
    } else {
      librariesWithSourceJarsAttached.remove(libraryKey);
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    for (LibraryKey libraryKey : librariesWithSourceJarsAttached) {
      Element libElement = new Element("library");
      libElement.setText(libraryKey.getIntelliJLibraryName());
      element.addContent(libElement);
    }
    return element;
  }

  @Override
  public void loadState(Element state) {
    Set<LibraryKey> librariesWithSourceJars = Sets.newHashSet();
    for (Element libElement : state.getChildren()) {
      LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(libElement.getText());
      librariesWithSourceJars.add(libraryKey);
    }
    this.librariesWithSourceJarsAttached = librariesWithSourceJars;
  }
}
