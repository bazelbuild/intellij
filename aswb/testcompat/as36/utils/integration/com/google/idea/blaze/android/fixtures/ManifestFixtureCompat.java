/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.fixtures;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.ElementPresentation;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Compat class used by {@link ManifestFixture} */
public class ManifestFixtureCompat {
  private ManifestFixtureCompat() {}

  public static AndroidAttributeValue<Integer> getVersionCode(Manifest manifest) {
    // Create No-op AndroidAttributeValue because AS 3.6 Manifest does not support versions.
    return new AndroidAttributeValue<Integer>() {
      @Override
      public @Nullable XmlAttribute getXmlAttribute() {
        return null;
      }

      @Override
      public @Nullable XmlAttributeValue getXmlAttributeValue() {
        return null;
      }

      @Override
      public @NotNull Converter<Integer> getConverter() {
        return null;
      }

      @Override
      public void setStringValue(String s) {}

      @Override
      public void setValue(Integer integer) {}

      @Override
      public @Nullable String getRawText() {
        return null;
      }

      @Override
      public @Nullable XmlTag getXmlTag() {
        return null;
      }

      @Override
      public @Nullable XmlElement getXmlElement() {
        return null;
      }

      @Override
      public DomElement getParent() {
        return null;
      }

      @Override
      public XmlTag ensureTagExists() {
        return null;
      }

      @Override
      public XmlElement ensureXmlElementExists() {
        return null;
      }

      @Override
      public void undefine() {}

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public boolean exists() {
        return false;
      }

      @Override
      public @NotNull DomGenericInfo getGenericInfo() {
        return null;
      }

      @Override
      public @NotNull String getXmlElementName() {
        return null;
      }

      @Override
      public @NotNull String getXmlElementNamespace() {
        return null;
      }

      @Override
      public @Nullable String getXmlElementNamespaceKey() {
        return null;
      }

      @Override
      public void accept(DomElementVisitor domElementVisitor) {}

      @Override
      public void acceptChildren(DomElementVisitor domElementVisitor) {}

      @Override
      public @NotNull DomManager getManager() {
        return null;
      }

      @Override
      public @NotNull Type getDomElementType() {
        return null;
      }

      @Override
      public AbstractDomChildrenDescription getChildDescription() {
        return null;
      }

      @Override
      public @NotNull DomNameStrategy getNameStrategy() {
        return null;
      }

      @Override
      public @NotNull ElementPresentation getPresentation() {
        return null;
      }

      @Override
      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @Override
      public <T extends DomElement> @Nullable T getParentOfType(Class<T> aClass, boolean b) {
        return null;
      }

      @Override
      public @Nullable Module getModule() {
        return null;
      }

      @Override
      public void copyFrom(DomElement domElement) {}

      @Override
      public <T extends DomElement> T createMockCopy(boolean b) {
        return null;
      }

      @Override
      public <T extends DomElement> T createStableCopy() {
        return null;
      }

      @Override
      public <T> @Nullable T getUserData(@NotNull Key<T> key) {
        return null;
      }

      @Override
      public <T> void putUserData(@NotNull Key<T> key, @Nullable T t) {}

      @Override
      public <T extends Annotation> @Nullable T getAnnotation(Class<T> aClass) {
        return null;
      }

      @Override
      public @Nullable String getStringValue() {
        return null;
      }

      @Nullable
      @Override
      public Integer getValue() {
        return 0;
      }
    };
  }

  public static UsesPermission addUsesPermission(Manifest manifest) {
    AndroidAttributeValue<String> noopAndroidAttrVal =
        new AndroidAttributeValue<String>() {
          @Override
          public @Nullable XmlAttribute getXmlAttribute() {
            return null;
          }

          @Override
          public @Nullable XmlAttributeValue getXmlAttributeValue() {
            return null;
          }

          @Override
          public @NotNull Converter<String> getConverter() {
            return null;
          }

          @Override
          public void setStringValue(String s) {}

          @Override
          public void setValue(String s) {}

          @Override
          public @Nullable String getRawText() {
            return null;
          }

          @Override
          public @Nullable XmlTag getXmlTag() {
            return null;
          }

          @Override
          public @Nullable XmlElement getXmlElement() {
            return null;
          }

          @Override
          public DomElement getParent() {
            return null;
          }

          @Override
          public XmlTag ensureTagExists() {
            return null;
          }

          @Override
          public XmlElement ensureXmlElementExists() {
            return null;
          }

          @Override
          public void undefine() {}

          @Override
          public boolean isValid() {
            return false;
          }

          @Override
          public boolean exists() {
            return false;
          }

          @Override
          public @NotNull DomGenericInfo getGenericInfo() {
            return null;
          }

          @Override
          public @NotNull String getXmlElementName() {
            return null;
          }

          @Override
          public @NotNull String getXmlElementNamespace() {
            return null;
          }

          @Override
          public @Nullable String getXmlElementNamespaceKey() {
            return null;
          }

          @Override
          public void accept(DomElementVisitor domElementVisitor) {}

          @Override
          public void acceptChildren(DomElementVisitor domElementVisitor) {}

          @Override
          public @NotNull DomManager getManager() {
            return null;
          }

          @Override
          public @NotNull Type getDomElementType() {
            return null;
          }

          @Override
          public AbstractDomChildrenDescription getChildDescription() {
            return null;
          }

          @Override
          public @NotNull DomNameStrategy getNameStrategy() {
            return null;
          }

          @Override
          public @NotNull ElementPresentation getPresentation() {
            return null;
          }

          @Override
          public GlobalSearchScope getResolveScope() {
            return null;
          }

          @Override
          public <T extends DomElement> @Nullable T getParentOfType(Class<T> aClass, boolean b) {
            return null;
          }

          @Override
          public @Nullable Module getModule() {
            return null;
          }

          @Override
          public void copyFrom(DomElement domElement) {}

          @Override
          public <T extends DomElement> T createMockCopy(boolean b) {
            return null;
          }

          @Override
          public <T extends DomElement> T createStableCopy() {
            return null;
          }

          @Override
          public <T> @Nullable T getUserData(@NotNull Key<T> key) {
            return null;
          }

          @Override
          public <T> void putUserData(@NotNull Key<T> key, @Nullable T t) {}

          @Override
          public <T extends Annotation> @Nullable T getAnnotation(Class<T> aClass) {
            return null;
          }

          @Override
          public @Nullable String getStringValue() {
            return null;
          }

          @Nullable
          @Override
          public String getValue() {
            return null;
          }
        };

    // Return no-op UsesPermission since as35 does not support `addUsesPermission`
    return new UsesPermission() {
      @Override
      public AndroidAttributeValue<String> getName() {
        return noopAndroidAttrVal;
      }

      @Override
      public @Nullable XmlTag getXmlTag() {
        return null;
      }

      @Override
      public @Nullable XmlElement getXmlElement() {
        return null;
      }

      @Override
      public DomElement getParent() {
        return null;
      }

      @Override
      public XmlTag ensureTagExists() {
        return null;
      }

      @Override
      public XmlElement ensureXmlElementExists() {
        return null;
      }

      @Override
      public void undefine() {}

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public boolean exists() {
        return false;
      }

      @Override
      public @NotNull DomGenericInfo getGenericInfo() {
        return null;
      }

      @Override
      public @NotNull String getXmlElementName() {
        return null;
      }

      @Override
      public @NotNull String getXmlElementNamespace() {
        return null;
      }

      @Override
      public @Nullable String getXmlElementNamespaceKey() {
        return null;
      }

      @Override
      public void accept(DomElementVisitor domElementVisitor) {}

      @Override
      public void acceptChildren(DomElementVisitor domElementVisitor) {}

      @Override
      public @NotNull DomManager getManager() {
        return null;
      }

      @Override
      public @NotNull Type getDomElementType() {
        return null;
      }

      @Override
      public AbstractDomChildrenDescription getChildDescription() {
        return null;
      }

      @Override
      public @NotNull DomNameStrategy getNameStrategy() {
        return null;
      }

      @Override
      public @NotNull ElementPresentation getPresentation() {
        return null;
      }

      @Override
      public GlobalSearchScope getResolveScope() {
        return null;
      }

      @Override
      public <T extends DomElement> @Nullable T getParentOfType(Class<T> aClass, boolean b) {
        return null;
      }

      @Override
      public @Nullable Module getModule() {
        return null;
      }

      @Override
      public void copyFrom(DomElement domElement) {}

      @Override
      public <T extends DomElement> T createMockCopy(boolean b) {
        return null;
      }

      @Override
      public <T extends DomElement> T createStableCopy() {
        return null;
      }

      @Override
      public <T> @Nullable T getUserData(@NotNull Key<T> key) {
        return null;
      }

      @Override
      public <T> void putUserData(@NotNull Key<T> key, @Nullable T t) {}

      @Override
      public <T extends Annotation> @Nullable T getAnnotation(Class<T> aClass) {
        return null;
      }
    };
  }
}
