package com.google.idea.blaze.gazelle;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "GazelleUserSettings", storages = {
        @Storage("gazelle.user.settings.xml")
})
public class GazelleUserSettings implements PersistentStateComponent<GazelleUserSettings> {

    private static final String DEFAULT_GAZELLE_TARGET = "";
    private String gazelleTarget = DEFAULT_GAZELLE_TARGET;

    public static GazelleUserSettings getInstance() { return ServiceManager.getService(GazelleUserSettings.class); }

    public void setGazelleTarget(String target) {
        this.gazelleTarget = target;
    }

    public void clearGazelleTarget() { this.gazelleTarget = DEFAULT_GAZELLE_TARGET; }

    public String getGazelleTarget() { return StringUtil.defaultIfEmpty(this.gazelleTarget, DEFAULT_GAZELLE_TARGET).trim();}

    @Override
    public @Nullable GazelleUserSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GazelleUserSettings state) {
        this.gazelleTarget = state.gazelleTarget;
    }
}
