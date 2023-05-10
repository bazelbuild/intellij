package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@State(name="BlazeCompilerInfoMap", storages = @Storage(value = "blazeInfo.xml", roamingType = RoamingType.DISABLED))
final public class BlazeCompilerInfoMapService implements PersistentStateComponent<BlazeCompilerInfoMapService.State> {
    final public static Logger logger = Logger.getInstance(BlazeCompilerInfoMapService.class);
    @Override
    public @Nullable BlazeCompilerInfoMapService.State getState() {
        return this.state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State{
        // We annotate non-public property in order to be saved using PersistentStateComponent interface
        // https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html
        @NotNull @Property
        ImmutableMap<String, String> compilerVersionStringMap = ImmutableMap.of();

        public State() {

        }

        public State(@NotNull ImmutableMap<String, String> compilerInfoMap) {
            this.compilerVersionStringMap = compilerInfoMap;
        }
    }

    BlazeCompilerInfoMapService(Project p) {

    }

    public void setState(ImmutableMap<String, String> compilerInfoMap) {
        this.state = new State(compilerInfoMap);
    }

    // We annotate non-public property in order to be saved using PersistentStateComponent interface
    // https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html
    @Property
    private State state = new State();

    @NotNull
    public static BlazeCompilerInfoMapService getInstance(Project p) {
        return p.getService(BlazeCompilerInfoMapService.class);
    }

    public boolean isClangTarget(Label label) {
        String result = getState().compilerVersionStringMap.get(label.toString());
        if(result == null) {
            logger.warn(String.format("Could not find version string for target %s", label));
            return false;
        }
        return result.contains("clang");
    }
}
