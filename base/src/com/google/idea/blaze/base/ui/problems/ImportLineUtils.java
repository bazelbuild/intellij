package com.google.idea.blaze.base.ui.problems;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.google.idea.blaze.base.ui.problems.ImportIssueConstants.*;
import static com.google.idea.blaze.base.ui.problems.ImportProblemContainerService.*;

public class ImportLineUtils {

    @NotNull
    public static String getPackageName(String originalLine) {
        String importWithoutKeywords = getImportLineWithoutKeywords(originalLine);
        String packageName = resolvePackageName(importWithoutKeywords);
        return packageName;
    }

    @NotNull
    private static String getImportLineWithoutKeywords(String originalLine) {
        return originalLine.
                replace(IMPORT_KEYWORD, EMPTY_STRING).
                replace(STATIC_KEYWORD, EMPTY_STRING).
                replace(JAVA_EOFL_IDENTIFIER, EMPTY_STRING).
                trim();
    }



    private static String resolvePackageName(String importWithoutKeywords) {
        String[] importLinePartsArray = importWithoutKeywords.split("\\.");
        List<String> importLineParts = Arrays.asList(importLinePartsArray);
        Stream<String> partsThatStartWithCapital = importLineParts.stream().filter(part -> startsWithUpperCase(part));
        long numberOfPartsThatStartWithUpperCase = partsThatStartWithCapital.count();

        if (isWildCardImportLineJava(importWithoutKeywords)) {
            return importWithoutKeywords.substring(0, importWithoutKeywords.lastIndexOf("."));
        } else if (isWildCardImportLineScala(importWithoutKeywords)) {
            return importWithoutKeywords.substring(0, importWithoutKeywords.lastIndexOf("."));
        } else if (isRegularImport(numberOfPartsThatStartWithUpperCase, isClassNameLast(importLinePartsArray))) {
            return importWithoutKeywords.substring(0, importWithoutKeywords.lastIndexOf("."));
        }
        return null;
    }

    public static boolean isWildCardImportLineJava(String importLine) {
        String lineWithoutDotComma = importLine.replace(JAVA_EOFL_IDENTIFIER, "");
        return lineWithoutDotComma.endsWith(JAVA_WILDCARD_KEYWORD) ;
    }

    public static boolean isWildCardImportLineScala(String importLine) {
        String lineWithoutDotComma = importLine.replace(JAVA_EOFL_IDENTIFIER, "");
        return lineWithoutDotComma.endsWith(SCALA_WILDCARD_KEYWORD);
    }


    private static boolean isRegularImport(long numberOfPartsThatStartWithUpperCase, boolean classNameLast) {
        return thereIsOneClassName(numberOfPartsThatStartWithUpperCase) && classNameLast;
    }

    private static boolean isClassNameLast(String[] parts) {
        return startsWithUpperCase(parts[parts.length-1]);
    }

    private static boolean thereIsOneClassName(long partsThatStartWithUpperCase) {
        return partsThatStartWithUpperCase == 1;
    }

    private static boolean startsWithUpperCase(String part) {
        return Character.isUpperCase(part.charAt(0));
    }


    public static boolean isWildCardImportLine(String importLine) {
        return isWildCardImportLineJava(importLine) || isWildCardImportLineScala(importLine);
    }
}

