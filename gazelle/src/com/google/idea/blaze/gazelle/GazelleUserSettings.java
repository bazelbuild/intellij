package com.google.idea.blaze.gazelle;

import com.google.idea.blaze.base.model.primitives.InvalidTargetException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "GazelleUserSettings",
    storages = {@Storage("gazelle.user.settings.xml")})
public class GazelleUserSettings implements PersistentStateComponent<GazelleUserSettings> {

  private static final String EMPTY_GAZELLE_TARGET = "";
  private String gazelleTarget = EMPTY_GAZELLE_TARGET;

  public static GazelleUserSettings getInstance() {
    return ServiceManager.getService(GazelleUserSettings.class);
  }

  public void clearGazelleTarget() {
    this.gazelleTarget = EMPTY_GAZELLE_TARGET;
  }

  public String getGazelleTarget() {
    return StringUtil.defaultIfEmpty(this.gazelleTarget, EMPTY_GAZELLE_TARGET).trim();
  }

  public void setGazelleTarget(String target) {
    this.gazelleTarget = target;
  }

  public Optional<Label> getGazelleTargetLabel() throws InvalidTargetException {
    String rawTarget = getGazelleTarget();
    if (rawTarget.equals(EMPTY_GAZELLE_TARGET)) {
      return Optional.empty();
    }
    Label targetLabel = (Label) TargetExpression.fromString(rawTarget);
    return Optional.of(targetLabel);
  }

  @Override
  public @Nullable GazelleUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GazelleUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
