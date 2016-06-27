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

"""Stamps a plugin xml with build information."""

import argparse
import re
from xml.dom.minidom import parse

parser = argparse.ArgumentParser()

parser.add_argument(
    "--plugin_xml",
    help="The plugin xml file",
    required=True,
)
parser.add_argument(
    "--build_txt",
    help="The build.txt file containing the build number, e.g. IC-144.1818",
    required=True,
)
parser.add_argument(
    "--stamp_since_build",
    action="store_true",
    help="Stamp since-build with the build number",
)
parser.add_argument(
    "--stamp_until_build",
    action="store_true",
    help="Stamp until-build with the build number",
)
parser.add_argument(
    "--version_file",
    help="Version file to stamp into the plugin.xml",
)
parser.add_argument(
    "--changelog_file",
    help="Changelog file to add to plugin.xml",
)
parser.add_argument(
    "--include_product_code_in_stamp",
    action="store_true",
    help="Include the product code in the stamp",
)

def _read_changelog(changelog_file):
  """Reads the changelog and transforms it into trivial HTML"""
  with open(changelog_file) as f:
    lines = ["<p>" + line + "</p>" for line in f.readlines()]
    return "\n".join(lines)


def main():

  args = parser.parse_args()

  dom = parse(args.plugin_xml)

  with open(args.build_txt) as f:
    build_number = f.read()

  new_elements = []

  idea_plugin = dom.documentElement

  match = re.match(r"^([A-Z]+-)?([0-9]+)(\.[0-9]+)?", build_number)
  if match is None:
    raise ValueError("Invalid build number: " + build_number)

  build_number = match.group(1) + match.group(2) + match.group(3)
  build_number_without_product_code = match.group(2) + match.group(3)

  version_element = None
  version_elements = idea_plugin.getElementsByTagName("version")
  if len(version_elements) > 1:
    raise ValueError("Ambigious version element")

  if len(version_elements) == 1:
    version_element = version_elements[0].firstChild

  if args.version_file:
    if version_element:
      raise ValueError("version element already in plugin.xml")
    version_element = dom.createElement("version")
    new_elements.append(version_element)
    with open(args.version_file) as f:
      value = f.read().strip()
      version_text = dom.createTextNode(value)
      version_element.appendChild(version_text)

  if args.stamp_since_build or args.stamp_until_build:
    if idea_plugin.getElementsByTagName("idea-version"):
      raise ValueError("idea-version element already present")

    idea_version_build_element = (build_number
                                  if args.include_product_code_in_stamp else
                                  build_number_without_product_code)

    idea_version_element = dom.createElement("idea-version")
    new_elements.append(idea_version_element)

    if args.stamp_since_build:
      idea_version_element.setAttribute("since-build",
                                        idea_version_build_element)
    if args.stamp_until_build:
      idea_version_element.setAttribute("until-build",
                                        idea_version_build_element)

  if args.changelog_file:
    if idea_plugin.getElementsByTagName("change-notes"):
      raise ValueError("change-notes element already in plugin.xml")
    changelog_element = dom.createElement("change-notes")
    changelog_text = dom.createCDATASection(_read_changelog(args.changelog_file))
    changelog_element.appendChild(changelog_text)
    new_elements.append(changelog_element)

  for new_element in new_elements:
    idea_plugin.appendChild(new_element)

  print dom.toxml()


if __name__ == "__main__":
  main()
