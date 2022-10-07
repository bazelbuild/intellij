package com.google.idea.blaze.cpp;

public class CompilerInfo {
    public CompilerInfo() {

    }

    public void setVersionString(String versionString) {
        this.versionString = versionString;
    }

    String versionString;

    public String getVersionString() {
        return versionString;
    }

    CompilerInfo(String versionString) {
        this.versionString = versionString;
    }
}
