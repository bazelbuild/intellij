package com.google.idea.blaze.base;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

public class InMemoryPreferencesFactory implements PreferencesFactory {
  private final Preferences system = new InMemoryPreferences(null, "");
  private final Preferences user = new InMemoryPreferences(null, "");

  @Override
  public Preferences systemRoot() {
    return system;
  }

  @Override
  public Preferences userRoot() {
    return user;
  }

  private static class InMemoryPreferences extends AbstractPreferences {
    private final Map<String, String> data = new HashMap<>();
    private final Map<String, InMemoryPreferences> children = new HashMap<>();

    private InMemoryPreferences(InMemoryPreferences parent, String name) {
      super(parent, name);
    }

    @Override
    protected void putSpi(String key, String value) {
      data.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
      return data.get(key);
    }

    @Override
    protected void removeSpi(String key) {
      data.remove(key);
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
      data.clear();
      children.clear();
    }

    @Override
    protected String[] keysSpi() throws BackingStoreException {
      return data.keySet().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() throws BackingStoreException {
      return children.keySet().toArray(new String[0]);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
      return children.computeIfAbsent(name, it -> new InMemoryPreferences(this, name));
    }

    @Override
    protected void syncSpi() throws BackingStoreException { }

    @Override
    protected void flushSpi() throws BackingStoreException { }
  }
}
