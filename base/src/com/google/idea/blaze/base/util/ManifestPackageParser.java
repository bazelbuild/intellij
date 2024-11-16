package com.google.idea.blaze.base.util;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManifestPackageParser {

  private static final Logger logger = Logger.getLogger(ManifestPackageParser.class.getName());

  public static String extractPackageName(String filePath) {
    logger.log(Level.INFO, "The manifest path is " + filePath);
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      StringBuilder content = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line).append("\n");
      }

      Pattern pattern =
          Pattern.compile("package\\s*=\\s*\"([a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_])\"");
      Matcher matcher = pattern.matcher(content.toString());

      if (matcher.find()) {
        String packageName = matcher.group(1);
        logger.log(Level.INFO, "The extracted package name is " + packageName);
        return packageName;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    logger.log(Level.INFO, "The extracted package name is null ");
    return null;
  }
}