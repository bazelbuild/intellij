/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

// Prerequisites:
//      go install github.com/bazelbuild/buildtools/buildozer@latest
//      export PATH=$~/go/bin/
// Usage: go run tools/bump_major/main.go
package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
)

var old = "2025.3"
var new = "2026.1"
var old_api = "253"
var new_api = "261"
var new_label = "2026_1"

var jsonPath = "override.json"
var overrideJson = `{
    "IsListArg": {
	  "exports":false,
	  "srcs":false
    }
}`

func createOverrideJson() {
	if _, err := os.Stat(jsonPath); os.IsNotExist(err) {
        os.WriteFile(jsonPath, []byte(overrideJson), 0660)
	}
}

func main() {
    createOverrideJson()

	runBuildozer(fmt.Sprintf("new config_setting clion-%s after clion-%s", new, old), "//intellij_platform_sdk:__pkg__")
	runBuildozer("set values {}", "//intellij_platform_sdk:clion-"+new+"")
	runBuildozer("dict_add values define:ij_product=clion-"+new+"", "//intellij_platform_sdk:clion-"+new+"")

	insertIntoSelect("exports", "//sdkcompat", fmt.Sprintf("\"clion-%s\":[\"//sdkcompat/v%s\"],", new, new_api))
	insertIntoSelect("srcs", "//third_party/python:python_helpers", fmt.Sprintf(`"clion-%s":["@python_%s//:python_helpers"],`, new, new_label))
	insertIntoSelect("exports", "//third_party/python:python_internal", fmt.Sprintf(`"clion-%s":["@python_%s//:python"],`, new, new_label))
	insertIntoSelect("exports", "//third_party/javascript:javascript_internal", fmt.Sprintf(`"clion-%s":["@clion_%s//:javascript"],`, new, new_label))
	insertIntoSelect("exports", "//third_party/javascript:css_internal", fmt.Sprintf(`"clion-%s":["@clion_%s//:css"],`, new, new_label))
	insertIntoSelect("exports", "//third_party/javascript:tslint_internal", fmt.Sprintf(`"clion-%s":["@clion_%s//:tslint"],`, new, new_label))
	// insertIntoSelect("exports", "//third_party/javascript:angular_internal", fmt.Sprintf(`"clion-%s":["@clion_%s//:angular"],`, new, new_label))

	getOutput([]string{"cp", "-R", "sdkcompat/v" + old_api + "/", "sdkcompat/v" + new_api})
	getOutput([]string{"cp", "-R", "intellij_platform_sdk/BUILD.clion" + old_api, "intellij_platform_sdk/BUILD.clion" + new_api})
	// getOutput([]string{"cp", "-R", "testing/testcompat/v" + old_api + "/", "testing/testcompat/v" + new_api})

	runBuildozer("set name v"+new_api, "//sdkcompat/v"+new_api+":v"+old_api)

	// MISSING: DIRECT_IJ_PRODUCTS in build_defs.bzl
	// MISSING: //intellij_platform_sdk:jsr305
	// MISSING: //testing:lib
	// MISSING: toml
	// MISSING: WORKSPACE.bzlmod
}

func insertIntoSelect(attribute string, target string, newLine string) {
	lines := getOutput([]string{"buildozer", "-tables", jsonPath, fmt.Sprintf("print %s", attribute), target})
	newLines := insert(lines, newLine, 1)
	command := fmt.Sprintf("set %s %s", attribute, strings.ReplaceAll(strings.Join(newLines, " "), " ", ""))
	runBuildozer(command, target)
}

func runBuildozer(command string, target string) []string {
	return getOutput([]string{"buildozer", "-tables", jsonPath, command, target})
}

func getOutput(cmdStrings []string) []string {
	cmd := exec.Command(cmdStrings[0], cmdStrings[1:]...)
	out, err := cmd.Output()
	if err != nil {
		panic(err)
	}
	lines := strings.Split(string(out), "\n")
	return lines
}

func insert(lines []string, newLine string, index int) []string {
	return append(lines[:index], append([]string{newLine}, lines[index:]...)...)
}
