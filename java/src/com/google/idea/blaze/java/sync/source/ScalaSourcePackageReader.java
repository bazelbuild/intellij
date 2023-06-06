package com.google.idea.blaze.java.sync.source;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class ScalaSourcePackageReader implements JavaPackageReader {

  private static final Logger logger = Logger.getInstance(ScalaSourcePackageReader.class);

  public static ScalaSourcePackageReader getInstance() {
    return ServiceManager.getService(ScalaSourcePackageReader.class);
  }

  // Package declaration of java-like languages.
  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)");

  @Nullable
  @Override
  public String getDeclaredPackageOfJavaFile(BlazeContext context,
      ArtifactLocationDecoder artifactLocationDecoder, SourceArtifact sourceArtifact) {
    if (sourceArtifact.artifactLocation.isGenerated()) {
      return null;
    }
    InputStreamProvider inputStreamProvider = InputStreamProvider.getInstance();
    File sourceFile = artifactLocationDecoder.resolveSource(sourceArtifact.artifactLocation);
    if (sourceFile == null) {
      return null;
    }
    boolean isScala = sourceFile.getName().endsWith(".scala");
    if (!isScala) {
      return null;
    }
    try (InputStream javaInputStream = inputStreamProvider.forFile(sourceFile)) {
      BufferedReader javaReader = new BufferedReader(new InputStreamReader(javaInputStream, UTF_8));
      String javaLine;
      List<String> packageDeclarations = Lists.newArrayList();
      while ((javaLine = javaReader.readLine()) != null) {
        Matcher packageMatch = PACKAGE_PATTERN.matcher(javaLine);
        if (packageMatch.find()) {
          packageDeclarations.add(packageMatch.group(1));
        }
      }
      if (!packageDeclarations.isEmpty()) {
        return String.join(".", packageDeclarations);
      }
      IssueOutput.warn("No package name string found in scala source file: " + sourceFile)
          .inFile(sourceFile)
          .submit(context);
      return null;
    } catch (FileNotFoundException e) {
      context.output(PrintOutput.log("No source file found for: " + sourceFile));
      return null;
    }
    catch (IOException e) {
      logger.error(e);
      return null;
    }
  }
}
