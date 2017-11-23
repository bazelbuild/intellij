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
package com.google.idea.blaze.kotlin.run;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;


/**
 * Suppresses certain non-Blaze configuration producers in Blaze projects.
 */
public class NonBlazeProducerSuppressor extends AbstractProjectComponent {
    //    private static final ImmutableList<Class<? extends RunConfigurationProducer<?>>> PRODUCERS_TO_SUPPRESS = ImmutableList.of(
//            KotlinJUnitRunConfigurationProducer.class,
//            KotlinPatternConfigurationProducer.class
//    );
//
    public NonBlazeProducerSuppressor(Project project) {
        super(project);
    }

    //
    @Override
    public void projectOpened() {
//        if (Blaze.isBlazeProject(myProject)) {
//            suppressProducers(myProject);
//        }
    }
//
//    private static void suppressProducers(Project project) {
//        RunConfigurationProducerService producerService =
//                RunConfigurationProducerService.getInstance(project);
//        PRODUCERS_TO_SUPPRESS.forEach(producerService::addIgnoredProducer);
//    }
}