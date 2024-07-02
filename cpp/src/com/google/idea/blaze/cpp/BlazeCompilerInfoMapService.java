package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
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

@State(name="BlazeCompilerInfoMap", storages = @Storage(value = "blazeInfo.xml", roamingType = RoamingType.DISABLED))
final public class BlazeCompilerInfoMapService implements PersistentStateComponent<BlazeCompilerInfoMapService.State> {
    final public static Logger logger = Logger.getInstance(BlazeCompilerInfoMapService.class);
    @Override
    public @Nullable BlazeCompilerInfoMapService.State getState() {
        return this.state;
    }

    @Override
    public void loadState(@NotNull State state) {
        if (state.compilerVersionStringMap.isEmpty()) {
            logger.error("Loading empty state - Some compiler flags may stop working");
        }
        this.state = state;
    }

    public static class State{
        // We annotate non-public property in order to be saved using PersistentStateComponent interface
        // https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html
        // State cannot be an ImmutableMap, because the IDE uses Map.clear() internally,
        // which ImmutableMap doesn't support.
        @NotNull @Property
        Map<String, String> compilerVersionStringMap = Collections.emptyMap();

        public State() {}

        public State(@NotNull Map<String, String> compilerInfoMap) {
            assert !(compilerInfoMap instanceof ImmutableMap);
            this.compilerVersionStringMap = compilerInfoMap;
        }
    }

    BlazeCompilerInfoMapService(Project p) {
    }

    public void setState(ImmutableMap<String, String> compilerInfoMap) {
        // Yes, this is inefficient and will copy the values.
        // However, we need the map to be anything BUT an ImmutableMap in this class
        // (see the comment on `State`'s constructor), but we want it to be an ImmutableMap everywhere else.
        // Copying here shouldn't be a big performance hit because it happens once after every sync.
        Map<String, String> mutableMap = Collections.checkedMap(compilerInfoMap, String.class, String.class);
        this.state = new State(mutableMap);
    }

    // We annotate non-public property in order to be saved using PersistentStateComponent interface
    // https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html
    @NotNull @Property
    private State state = new State();

    @NotNull
    public static BlazeCompilerInfoMapService getInstance(Project p) {
        return p.getService(BlazeCompilerInfoMapService.class);
    }

    public boolean isClangTarget(Label label) {
        Map<String, String> compilerVersionStringMap = getState().compilerVersionStringMap;
        if (compilerVersionStringMap.isEmpty()) {
            logger.error("Compiler version mappings are uninitialized. This is likely because of an error de-serializing the state. Please check the logs for another error message.");
        }
        String result = compilerVersionStringMap.get(label.toString());
        if(result == null) {
            logger.warn(String.format("Could not find version string for target %s", label));
            return false;
        }
        return CompilerVersionUtil.isClang(result);
    }
}
