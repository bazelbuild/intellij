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

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.*
import com.google.idea.blaze.base.buildview.IssueReportingMode
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.intellij.build.events.MessageEvent
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class ActionCompletedParser : BuildEventParser {
  private fun getDescription(body: ActionExecuted): String? {
    if (!body.hasStderr()) {
      if (body.hasFailureDetail()) {
        return body.failureDetail.message
      }

      return null
    }

    val uri = try {
      URI.create(body.stderr.uri)
    } catch (e: IllegalArgumentException) {
      return "Invalid output URI: ${body.stderr.uri}"
    }

    return try {
      Files.readString(Path.of(uri))
    } catch (e: IOException) {
      "Could not read output file: ${e.message}"
    }
  }

	override fun parse(event: BuildEvent, issueReportingMode: IssueReportingMode): IssueOutput? {
		if (!event.id.hasActionCompleted()) return null
		val id = event.id.actionCompleted

		val isExternal = !id.label.startsWith("//")

		if (!event.hasAction()) return null
		val body = event.action

		val isWarning = !body.hasFailureDetail()

		// ignore warnings from external projects
		if (isExternal && isWarning) return null

		val name = if (isWarning) {
			"BUILD_WARNING"
		} else {
			"BUILD_FAILURE"
		}

		val issue = BazelBuildIssue(
			label = id.label,
			title = "$name: ${id.label}",
			description = getDescription(body) ?: return null,
		)

		val kind = when (issueReportingMode) {
			IssueReportingMode.SYNC -> {
				if (isWarning) MessageEvent.Kind.INFO else MessageEvent.Kind.WARNING
			}
			IssueReportingMode.BUILD -> {
				if (isWarning) MessageEvent.Kind.WARNING else MessageEvent.Kind.ERROR
			}
		}

		return IssueOutput(issue, kind)
	}
}
