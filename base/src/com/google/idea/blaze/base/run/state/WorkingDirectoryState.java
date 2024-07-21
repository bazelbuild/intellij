package com.google.idea.blaze.base.run.state;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import org.jdom.Element;

/**
 * State to enable setting a Working Directory for run configurations. Currently, it's only
 * supported for CLion debug runs. When every debugger supports it, it can be moved into the general
 * {@link RunConfigurationState}.
 */
public class WorkingDirectoryState implements RunConfigurationState {

  private static final String WORKING_DIRECTORY_ATTR = "working-directory";

  @Nullable
  private Optional<Path> workingDirectory = Optional.empty();

  @Nullable
  public Optional<Path> getWorkingDirectory() {
    return workingDirectory;
  }

  public void setWorkingDirectory(@Nullable String workingDirectory) {
    if (!Strings.isNullOrEmpty(workingDirectory)) {
      this.workingDirectory = Optional.of(Path.of(workingDirectory));
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    String attr = element.getAttributeValue(WORKING_DIRECTORY_ATTR);
    if (Strings.isNullOrEmpty(attr)) {
      workingDirectory = Optional.empty();
    } else {
      workingDirectory = Optional.of(Path.of(element.getAttributeValue(WORKING_DIRECTORY_ATTR)));
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (workingDirectory.isEmpty()) {
      element.removeAttribute(WORKING_DIRECTORY_ATTR);
    } else {
      element.setAttribute(WORKING_DIRECTORY_ATTR, workingDirectory.get().toString());
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new WorkingDirectoryStateEditor();
  }

  private static class WorkingDirectoryStateEditor implements RunConfigurationStateEditor {

    private final TextFieldWithBrowseButton component = new TextFieldWithBrowseButton();

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      WorkingDirectoryState state = (WorkingDirectoryState) genericState;
      component.setText(state.getWorkingDirectory().map(Path::toString).orElse(""));
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      WorkingDirectoryState state = (WorkingDirectoryState) genericState;
      state.setWorkingDirectory(component.getText());
    }

    @Override
    public JComponent createComponent() {
      LabeledComponent withLabel = new LabeledComponent<TextFieldWithBrowseButton>();
      withLabel.setText("Working directory (only set when debugging):");
      component.addBrowseFolderListener(new TextBrowseFolderListener(
          FileChooserDescriptorFactory.createSingleFolderDescriptor()));
      withLabel.setComponent(component);
      return UiUtil.createBox(withLabel);
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      component.setEnabled(enabled);
    }
  }
}
