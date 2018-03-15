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
package com.google.idea.blaze.java.fastbuild;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

final class FastBuildCompilerFactoryImpl implements FastBuildCompilerFactory {

  private static final String JAVAC_CLASS = "com.sun.tools.javac.Main";

  private final BlazeProjectDataManager projectDataManager;

  FastBuildCompilerFactoryImpl(BlazeProjectDataManager projectDataManager) {
    this.projectDataManager = projectDataManager;
  }

  @Override
  public FastBuildCompiler getCompilerFor(TargetIdeInfo targetIdeInfo) throws FastBuildException {
    File javacJar = getJavacJar(targetIdeInfo);
    return createCompiler(javacJar);
  }

  private File getJavacJar(TargetIdeInfo targetIdeInfo) throws FastBuildException {
    BlazeProjectData projectData = projectDataManager.getBlazeProjectData();
    checkState(projectData != null, "not a blaze project");

    List<JavaToolchainIdeInfo> javaToolchains = new ArrayList<>();
    for (Dependency dependency : targetIdeInfo.dependencies) {
      TargetIdeInfo depInfo = projectData.targetMap.get(dependency.targetKey);
      if (depInfo != null && depInfo.javaToolchainIdeInfo != null) {
        javaToolchains.add(depInfo.javaToolchainIdeInfo);
      }
    }
    if (javaToolchains.isEmpty()) {
      throw new FastBuildException(
          "Couldn't find a Java toolchain for target " + targetIdeInfo.key.label);
    }
    if (javaToolchains.size() > 1) {
      throw new FastBuildException(
          "Found multiple Java toolchains for target " + targetIdeInfo.key.label);
    }
    return projectData.artifactLocationDecoder.decode(javaToolchains.get(0).javacJar);
  }

  private FastBuildCompiler createCompiler(File javacJar) throws FastBuildException {
    try {
      URL url = javacJar.toURI().toURL();
      URLClassLoader urlClassLoader = new URLClassLoader(new URL[] {new URL("jar:" + url + "!/")});
      Class<?> javacClass = urlClassLoader.loadClass(JAVAC_CLASS);
      Object javacMain = javacClass.getDeclaredConstructor().newInstance();
      Method compileMethod = javacClass.getMethod("compile", String[].class, PrintWriter.class);
      return new ReflectiveJavac(javacMain, compileMethod);
    } catch (MalformedURLException | ReflectiveOperationException e) {
      throw new FastBuildException(e);
    }
  }

  private static class ReflectiveJavac implements FastBuildCompiler {

    private final Object javacObject;
    private final Method compileMethod;

    private ReflectiveJavac(Object javacObject, Method compileMethod) {
      this.javacObject = javacObject;
      this.compileMethod = compileMethod;
    }

    @Override
    public void compile(CompileInstructions compileInstructions) throws FastBuildCompileException {
      List<String> args =
          ImmutableList.<String>builder()
              .add("-d")
              .add(compileInstructions.outputDirectory().getPath())
              .add("-cp")
              .add(Joiner.on(':').join(transform(compileInstructions.classpath(), File::getPath)))
              .addAll(transform(compileInstructions.filesToCompile(), File::getPath))
              .build();
      try {
        Integer result =
            (Integer)
                compileMethod.invoke(
                    javacObject, args.toArray(new String[0]), compileInstructions.outputWriter());
        if (result != 0) {
          throw new FastBuildCompileException("javac exited with code " + result);
        }
      } catch (ReflectiveOperationException e) {
        throw new FastBuildCompileException(e);
      }
    }
  }
}
