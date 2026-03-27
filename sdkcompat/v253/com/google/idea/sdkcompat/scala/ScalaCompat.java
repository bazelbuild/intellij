package com.google.idea.sdkcompat.scala;

import org.jetbrains.plugins.scala.project.ScalaLibraryProperties;
import scala.Option;
import scala.collection.immutable.Seq$;

import java.io.File;

public class ScalaCompat {
    public static ScalaLibraryProperties scalaLibraryProperties(Option<String> libraryVersion) {
        return ScalaLibraryProperties.apply(libraryVersion, Seq$.MODULE$.<File>empty(), Seq$.MODULE$.<File>empty());
    }
}
