package com.google.idea.blaze.base.ui.problems;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.idea.blaze.base.ui.problems.ImportIssueConstants.*;
import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.ImportType.*;
import static com.google.idea.blaze.base.ui.problems.ImportProblemContainerService.EMPTY_STRING;

public class ImportLineUtils {


    @NotNull
    public static String getPackageName(String originalLine) {
        String packageName = resolvePackageName(originalLine);
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


    private static String resolvePackageName(String originalLine) {
        ImportType importType = getImportType(originalLine);
            switch (importType) {
            case REGULAR:
            case JAVA_WILDCARD:
            case SCALA_WILDCARD:
            case MULTIPLE_SCALA:
                String importWithoutKeywords = getImportLineWithoutKeywords(originalLine);
                return importWithoutKeywords.substring(0, importWithoutKeywords.lastIndexOf(PACKAGE_SEPARATOR));
            default:
                throw new RuntimeException("Issue resolving package " + originalLine);
        }
    }

    private static boolean isMultipleClassesImport(String importWithoutKeywords) {
        return Pattern.compile(".*\\{.*\\}.*").matcher(importWithoutKeywords).find();
    }

    public static boolean isWildCardImportLineJava(String importLine) {
        String lineWithoutDotComma = importLine.replace(JAVA_EOFL_IDENTIFIER, "");
        return lineWithoutDotComma.endsWith(JAVA_WILDCARD_KEYWORD);
    }

    public static boolean isWildCardImportLineScala(String importLine) {
        String lineWithoutDotComma = importLine.replace(JAVA_EOFL_IDENTIFIER, "");
        return lineWithoutDotComma.endsWith(SCALA_WILDCARD_KEYWORD);
    }


    private static boolean isRegularImport(long numberOfPartsThatStartWithUpperCase, boolean classNameLast) {
        return thereIsOneClassName(numberOfPartsThatStartWithUpperCase) && classNameLast;
    }

    private static boolean isClassNameLast(String[] parts) {
        return startsWithUpperCase(parts[parts.length - 1]);
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

    public static ImportType getImportType(String originalLine) {
        String importWithoutKeywords = getImportLineWithoutKeywords(originalLine);

        String[] importLinePartsArray = importWithoutKeywords.split("\\.");
        List<String> importLineParts = Arrays.asList(importLinePartsArray);
        Stream<String> partsThatStartWithCapital = importLineParts.stream().filter(part -> startsWithUpperCase(part));
        long numberOfPartsThatStartWithUpperCase = partsThatStartWithCapital.count();

        if (isWildCardImportLineJava(importWithoutKeywords)) {
            return JAVA_WILDCARD;
        } else if (isWildCardImportLineScala(importWithoutKeywords)) {
            return SCALA_WILDCARD;
        } else if (isMultipleClassesImport(importWithoutKeywords)) {
            return MULTIPLE_SCALA;
        } else if (isRegularImport(numberOfPartsThatStartWithUpperCase, isClassNameLast(importLinePartsArray))) {
            return REGULAR;
        }
        return null;
    }

    public static List<String> getClassNames(String originalLine, ImportType importType) {
        if (importType != MULTIPLE_SCALA) {
            throw new RuntimeException(
                    "Trying to parse multiple scala classes when import type is [" + importType.name() + "], and import line [" + originalLine + "]"
            );
        }
        String importWithoutKeywords = getImportLineWithoutKeywords(originalLine);

        String[] classNames = importWithoutKeywords.substring(importWithoutKeywords.lastIndexOf(PACKAGE_SEPARATOR ) + 1).
                replace(SCALA_MULTIPLE_START_IDENTIFIER, EMPTY_STRING).
                replace(SCALA_MULTIPLE_END_IDENTIFIER, EMPTY_STRING).
                split(SCALA_MULTIPLE_CLASS_SEPARATOR);

        List<String> classNamesList = Arrays.asList(classNames);
        List<String> classNamesListTrimmed = classNamesList.stream().map(String::trim).collect(Collectors.toList());
        return classNamesListTrimmed;
    }

    public enum ImportType {
        REGULAR, JAVA_WILDCARD, SCALA_WILDCARD, STATIC, ALIAS, MULTIPLE_SCALA, SCALA_OBJECTS
    }
}

