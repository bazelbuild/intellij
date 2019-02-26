/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.openapi;

/**
 * To work around a serious performance regression in 2018.3 (IDEA-205934), we're running a manual
 * save from inside a write action (typically a bad thing to do).
 *
 * <p>This doesn't work and isn't needed in 2018.2, so it's in sdkcompat. #api183: remove when the
 * upstream bug is fixed.
 */
public class SaveFromInsideWriteAction {

  public static void saveAll() {}
}
