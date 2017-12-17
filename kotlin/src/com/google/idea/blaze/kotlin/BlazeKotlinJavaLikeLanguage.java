package com.google.idea.blaze.kotlin;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;

import java.util.Set;

public class BlazeKotlinJavaLikeLanguage implements JavaLikeLanguage {
    @Override
    public Set<String> getFileExtensions() {
        return ImmutableSet.of(".kt");
    }
}
