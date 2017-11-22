/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang;

import com.google.idea.common.experiments.BoolExperiment;

/**
 * Controls whether Blaze go-lang support is activated. If this is disabled, we make no attempt to
 * resolve Blaze-specific import formats, etc.
 *
 * <p>If this is enabled, we override some default Go plugin behaviors.
 */
public class BlazeGoSupport {

  public static final BoolExperiment blazeGoSupportEnabled =
      new BoolExperiment("blaze.go.support.enabled", true);
}
