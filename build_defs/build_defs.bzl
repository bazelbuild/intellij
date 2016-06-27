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

""" Description: Custom build macros for plugin.xml handling """
#

load("//build_defs/shared:build_defs.bzl",
     "merged_plugin_xml_impl",
     "stamped_plugin_xml_impl",
     "intellij_plugin_impl")

def merged_plugin_xml(name, srcs):
    """Merges N plugin.xml files together
    """
    merged_plugin_xml_impl(
        name = name,
        srcs = srcs,
        merge_tool = "//build_defs/shared:merge_xml",
    )

def stamped_plugin_xml(name, plugin_xml,
                       stamp_since_build=False,
                       stamp_until_build=False,
                       version_file=None,
                       changelog_file=None,
                       include_product_code_in_stamp=False):
    """Stamps a plugin xml file with the IJ build number.
      stamp_since_build -- Add build number to idea-version since-build.
      stamp_until_build -- Add build number to idea-version until-build.
      version_file -- A file with the version number to be included.
      changelog_file -- A file with changelog to be included.
      include_product_code_in_stamp -- Whether the product code (eg. "IC")
        is included in since-build and until-build.
    """
    stamped_plugin_xml_impl(
        name = name,
        build_txt = "//intellij-platform-sdk:build_number",
        stamp_tool = "//build_defs/shared:stamp_plugin_xml",
        plugin_xml = plugin_xml,
        stamp_since_build = stamp_since_build,
        version_file = version_file,
        changelog_file = changelog_file,
        include_product_code_in_stamp = include_product_code_in_stamp
    )

def intellij_plugin(name, plugin_xml, deps):
    """ Creates an intellij plugin from the given deps and plugin.xml """
    intellij_plugin_impl(
        name = name,
        plugin_xml = plugin_xml,
        zip_tool = "//tools/zip",
        deps = deps,
    )

