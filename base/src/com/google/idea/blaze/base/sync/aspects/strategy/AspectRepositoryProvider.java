package com.google.idea.blaze.base.sync.aspects.strategy;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.apache.velocity.app.Velocity;

import java.io.File;
import java.util.List;
import java.util.Optional;

public interface AspectRepositoryProvider {
  ExtensionPointName<AspectRepositoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectRepositoryProvider");

  String OVERRIDE_REPOSITORY_FLAG = "--override_repository=intellij_aspect";
  String OVERRIDE_REPOSITORY_VARIADIC_FLAG = "--override_repository=intellij_aspect_variadic";

  Optional<File> aspectDirectory(Project project);

  static Optional<File> findAspectDirectory(Project project) {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
        .map(aspectRepositoryProvider -> aspectRepositoryProvider.aspectDirectory(project))
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  static Optional<File> findVariadicAspectDirectory(Project project) {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
            .map(aspectRepositoryProvider -> aspectRepositoryProvider.variadicAspectDirectory(project))
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
  }

  static Optional<List<String>> getOverrideFlag(Project project) {

    var manager =
            BlazeProjectDataManager.getInstance(project);

    if(manager == null) {
      return Optional.empty();
    }

    var projectData = manager.getBlazeProjectData();
    if (projectData == null) {
      return Optional.empty();
    }
    var languages = projectData.getWorkspaceLanguageSettings().getActiveLanguages();
   // if (settings.isLanguageActive(LanguageClass.JAVA)) {
 //    System.out.println("Java supported");
 //  }

    return findAspectDirectory(project).map(it -> OVERRIDE_REPOSITORY_FLAG + "=" + it.getPath())
            .flatMap(it1 ->
                    findVariadicAspectDirectory(project).map(it -> OVERRIDE_REPOSITORY_VARIADIC_FLAG + "=" + it.getPath()).map(
                            it2 -> List.of(it1, it2)
                    ));
  }

  Optional<File> variadicAspectDirectory(Project project);
}
