/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/** Class to manage icons used by the Blaze plugin. */
public class BlazeIcons {

  private static final String BASE = "/";

  public static final Icon Blaze = load("base/resources/icons/blaze.png"); // 16x16
  public static final Icon BlazeSlow = load("base/resources/icons/blaze_slow.png"); // 16x16
  public static final Icon BlazeDirty = load("base/resources/icons/blaze_dirty.png"); // 16x16
  public static final Icon BlazeClean = load("base/resources/icons/blaze_clean.png"); // 16x16
  public static final Icon BlazeFailed = load("base/resources/icons/blaze_failed.png"); // 16x16

  public static final Icon BlazeRerun = load("base/resources/icons/blazeRerun.png"); // 16x16

  // This is just the Blaze icon scaled down to the size IJ wants for tool windows.
  public static final Icon BlazeToolWindow =
      load("base/resources/icons/blazeToolWindow.png"); // 13x13

  public static final Icon BazelLeaf = load("base/resources/icons/bazel_leaf.png"); // 16x16

  // Build file support icons
  public static final Icon BuildFile = load("base/resources/icons/build_file.png"); // 16x16
  public static final Icon BuildRule = load("base/resources/icons/build_rule.png"); // 16x16

  public static final Icon LightningOverlay =
      load("base/resources/icons/lightningOverlay.png"); // 16x16

  private static Icon load(String path) {
    return IconLoader.getIcon(BASE + path, BlazeIcons.class);
  }
}
