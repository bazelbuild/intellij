# Copyright 2016 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Custom build macros for IntelliJ plugin handling.
"""

def merged_plugin_xml_impl(name, srcs, merge_tool):
  """Merges N plugin.xml files together."""
  native.genrule(
      name = name,
      srcs = srcs,
      outs = [name + ".xml"],
      cmd = "./$(location {merge_tool}) $(SRCS) > $@".format(
          merge_tool=merge_tool,
      ),
      tools = [merge_tool],
  )

def _optstr(name, value):
  return ("--" + name) if value else ""

def stamped_plugin_xml_impl(name,
                            plugin_xml,
                            build_txt,
                            stamp_tool,
                            stamp_since_build=False,
                            stamp_until_build=False,
                            version_file=None,
                            changelog_file=None,
                            include_product_code_in_stamp=False):
  """Stamps a plugin xml file with the IJ build number.

  Args:
    name: name of this rule
    plugin_xml: target plugin_xml to stamp
    build_txt: the file containing the build number
    stamp_tool: the tool to use to stamp the version
    stamp_since_build: Add build number to idea-version since-build.
    stamp_until_build: Add build number to idea-version until-build.
    version_file: A file with the version number to be included.
    changelog_file: A file with the changelog to be included.
    include_product_code_in_stamp: Whether the product code (eg. "IC")
        is included in since-build and until-build.
  """
  args = [
      "./$(location {stamp_tool})",
      "--plugin_xml=$(location {plugin_xml})",
      "--build_txt=$(location {build_txt})",
      "{stamp_since_build}",
      "{stamp_until_build}",
      "{include_product_code_in_stamp}",
  ]
  srcs = [plugin_xml, build_txt]

  if version_file:
    args.append("--version_file=$(location {version_file})")
    srcs.append(version_file)

  if changelog_file:
    args.append("--changelog_file=$(location {changelog_file})")
    srcs.append(changelog_file)

  cmd = " ".join(args).format(
          plugin_xml=plugin_xml,
          build_txt=build_txt,
          stamp_tool=stamp_tool,
          stamp_since_build=_optstr("stamp_since_build",
                                    stamp_since_build),
          stamp_until_build=_optstr("stamp_until_build",
                                    stamp_until_build),
          version_file=version_file,
          changelog_file=changelog_file,
          include_product_code_in_stamp=_optstr(
              "include_product_code_in_stamp",
              include_product_code_in_stamp)
      ) + "> $@"

  native.genrule(
      name = name,
      srcs = srcs,
      outs = [name + ".xml"],
      cmd = cmd,
      tools = [stamp_tool],
  )

def intellij_plugin_impl(name, plugin_xml, zip_tool, deps):
  """Creates an intellij plugin from the given deps and plugin.xml."""
  binary_name = name + "_binary"
  deploy_jar = binary_name + "_deploy.jar"
  native.java_binary(
      name = binary_name,
      runtime_deps = deps,
      create_executable = 0,
  )
  native.genrule(
      name = name + "_genrule",
      srcs = [plugin_xml, deploy_jar],
      tools = [zip_tool],
      outs = [name + ".jar"],
      cmd = " ; ".join([
          "cp $(location {deploy_jar}) $@",
          "chmod +w $@",
          "mkdir -p META-INF",
          "cp $(location {plugin_xml}) META-INF/plugin.xml",
          "$(location {zip_tool}) -u $@ META-INF/plugin.xml >/dev/null",
      ]).format(
          deploy_jar=deploy_jar,
          plugin_xml=plugin_xml,
          zip_tool=zip_tool,
      ),
  )

  # included (with tag) as a hack so that IJwB can recognize this is an intellij plugin
  native.java_import(
      name = name,
      jars = [name + ".jar"],
      tags = ["intellij-plugin"],
      visibility = ["//visibility:public"],
  )

