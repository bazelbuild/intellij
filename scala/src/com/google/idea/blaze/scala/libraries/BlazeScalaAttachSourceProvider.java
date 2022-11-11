package com.google.idea.blaze.scala.libraries;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.java.libraries.AttachedSourceJarManager;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.scala.sync.model.BlazeScalaSyncData;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.util.Transactions;
import com.google.idea.sdkcompat.general.BaseSdkCompat;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

public class BlazeScalaAttachSourceProvider implements AttachSourcesProvider {

  private static final BoolExperiment attachAutomatically =
      new BoolExperiment("blaze.attach.source.jars.automatically.3", true);

  @Override
  public Collection<AttachSourcesAction> getActions(
      List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    Project project = psiFile.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    List<BlazeLibrary> librariesToAttachSourceTo = Lists.newArrayList();
    for (LibraryOrderEntry orderEntry : orderEntries) {
      Library library = orderEntry.getLibrary();
      if (library == null) {
        continue;
      }
      String name = library.getName();
      if (name == null) {
        continue;
      }
      LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(name);
      if (AttachedSourceJarManager.getInstance(project).hasSourceJarAttached(libraryKey)) {
        continue;
      }
      BlazeJarLibrary blazeLibrary =
          findLibraryFromIntellijLibrary(project, blazeProjectData, library);
      if (blazeLibrary == null) {
        continue;
      }
      LibraryArtifact libraryArtifact = blazeLibrary.libraryArtifact;
      if (libraryArtifact.getSourceJars().isEmpty()) {
        continue;
      }
      librariesToAttachSourceTo.add(blazeLibrary);
    }

    if (librariesToAttachSourceTo.isEmpty()) {
      return ImmutableList.of();
    }

    // Hack: When sources are requested and we have them, we attach them automatically in the
    // background.
    if (attachAutomatically.getValue()) {
      TransactionGuard.getInstance()
          .submitTransactionLater(
              project,
              () -> {
                attachSources(project, blazeProjectData, librariesToAttachSourceTo);
              });
      return ImmutableList.of();
    }

    return ImmutableList.of(
        new AttachSourcesAction() {
          @Override
          public String getName() {
            return "Attach Blaze Source Jars";
          }

          @Override
          public String getBusyText() {
            return "Attaching source jars...";
          }

          @Override
          public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
            ActionCallback callback =
                new ActionCallback().doWhenDone(() -> navigateToSource(psiFile));
            Transactions.submitTransaction(
                project,
                () -> {
                  attachSources(project, blazeProjectData, librariesToAttachSourceTo);
                  callback.setDone();
                });
            return callback;
          }
        });
  }

  private static void navigateToSource(PsiFile psiFile) {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              PsiFile psi = refreshPsiFile(psiFile);
              if (psi != null && psi.canNavigate()) {
                psi.navigate(false);
              }
            });
  }

  /**
   * The previous {@link PsiFile} can be invalidated when source jars are attached.
   */
  @Nullable
  private static PsiFile refreshPsiFile(PsiFile psiFile) {
    return PsiManager.getInstance(psiFile.getProject())
        .findFile(psiFile.getViewProvider().getVirtualFile());
  }

  private static void attachSources(
      Project project,
      BlazeProjectData blazeProjectData,
      Collection<BlazeLibrary> librariesToAttachSourceTo) {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              IdeModifiableModelsProvider modelsProvider =
                  BaseSdkCompat.createModifiableModelsProvider(project);
              for (BlazeLibrary blazeLibrary : librariesToAttachSourceTo) {
                // Make sure we don't do it twice
                if (AttachedSourceJarManager.getInstance(project)
                    .hasSourceJarAttached(blazeLibrary.key)) {
                  continue;
                }
                AttachedSourceJarManager.getInstance(project)
                    .setHasSourceJarAttached(blazeLibrary.key, true);
                LibraryEditor.updateLibrary(
                    project,
                    blazeProjectData.getArtifactLocationDecoder(),
                    modelsProvider,
                    blazeLibrary);
              }
              modelsProvider.commit();
            });
  }

  @Nullable
  private BlazeJarLibrary findLibraryFromIntellijLibrary(
      Project project, BlazeProjectData blazeProjectData, Library library) {
    String libName = library.getName();
    if (libName == null) {
      return null;
    }
    LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(libName);
    BlazeScalaSyncData syncData = blazeProjectData.getSyncState().get(BlazeScalaSyncData.class);
    if (syncData == null) {
      Messages.showErrorDialog(project, "Project isn't synced. Please resync project.", "Error");
      return null;
    }
    return syncData.getImportResult().libraries.get(libraryKey);
  }
}
