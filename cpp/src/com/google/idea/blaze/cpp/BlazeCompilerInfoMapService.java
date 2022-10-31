package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

@State(name="BlazeCompilerInfoMap", storages = @Storage(value = "blazeInfo.xml", roamingType = RoamingType.DISABLED))
final public class BlazeCompilerInfoMapService implements PersistentStateComponent<BlazeCompilerInfoMapService.State> {
    @Override
    public @Nullable BlazeCompilerInfoMapService.State getState() {
        return this.state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State{
        public Map<String, CompilerInfo> compilerInfoMap;

        public State() {

        }

        public Map<String, CompilerInfo> getCompilerInfoMap() {
            return compilerInfoMap;
        }

        public State(Map<String, CompilerInfo> compilerInfoMap) {
            this.compilerInfoMap = compilerInfoMap;
        }
    }

    BlazeCompilerInfoMapService(Project p) {

    }

    public void setState(Map<TargetKey, CompilerInfo> compilerInfoMap) {
        this.state = new State(compilerInfoMap.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getLabel().toString(), Map.Entry::getValue)));
    }
    State state = new State(null);

    public static BlazeCompilerInfoMapService getInstance(Project p) {
        return p.getService(BlazeCompilerInfoMapService.class);
    }

}
