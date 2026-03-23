/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.buildview.events

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.idea.blaze.base.buildview.IssueReportingMode
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

internal val LOG = logger<BuildEventParser>()

interface BuildEventParser {
	companion object {
		val EP_NAME: ExtensionPointName<BuildEventParser> =
			ExtensionPointName.create("com.google.idea.blaze.BuildEventParser")

		fun parse(event: BuildEvent, issueReportingMode: IssueReportingMode): IssueOutput? {
			return EP_NAME.extensions.firstNotNullOfOrNull { it.parse(event, issueReportingMode) }
		}
	}

	fun parse(event: BuildEvent, issueReportingMode: IssueReportingMode): IssueOutput?
}
