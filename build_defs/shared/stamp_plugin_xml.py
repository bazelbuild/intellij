"""Stamps a plugin xml with build information."""

import argparse
import re
from xml.dom.minidom import parse
from xml.dom.minidom import parseString
import zipfile

parser = argparse.ArgumentParser()

parser.add_argument(
    "--plugin_xml",
    help="The plugin xml file",
    required=True,
)
parser.add_argument(
    "--application_info_jar",
    help="The jar file containing the application info xml",
    required=True,
)
parser.add_argument(
    "--application_info_name",
    help="A .txt file containing the application info xml name",
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
    "--plugin_id",
    help="plugin ID to stamp into the plugin.xml",
)
parser.add_argument(
    "--plugin_name",
    help="plugin name to stamp into the plugin.xml",
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
  """Reads the changelog and transforms it into trivial HTML."""
  with open(changelog_file) as f:
    return "\n".join("<p>" + line + "</p>" for line in f.readlines())


def _parse_build_number(build_number):
  """Parses the build number.

  Args:
    build_number: The build number as text.
  Returns:
    build_number, build_number_without_product_code.
  Raises:
    ValueError: if the build number is invalid.
  """
  match = re.match(r"^([A-Z]+-)?([0-9]+)(\.[0-9]+)?", build_number)
  if match is None:
    raise ValueError("Invalid build number: " + build_number)

  build_number = match.group(1) + match.group(2) + match.group(3)
  build_number_without_product_code = match.group(2) + match.group(3)
  return build_number, build_number_without_product_code


def main():

  args = parser.parse_args()

  dom = parse(args.plugin_xml)

  with open(args.application_info_name) as f:
    application_info_name = f.read().strip()

  with zipfile.ZipFile(args.application_info_jar, "r") as zf:
    try:
      data = zf.read(application_info_name)
    except:
      raise ValueError("Could not read application info file: " +
                       application_info_name)
    component = parseString(data)

    build_elements = component.getElementsByTagName("build")
    if not build_elements:
      raise ValueError("Could not find <build> element.")
    if len(build_elements) > 1:
      raise ValueError("Ambiguous <build> element.")
    build_element = build_elements[0]

    attrs = build_element.attributes
    if attrs.has_key("apiVersion"):
      api_version_attr = attrs.get("apiVersion")
    else:
      api_version_attr = attrs.get("number")

  if not api_version_attr:
    raise ValueError("Could not find api version in application info")

  api_version, api_version_without_product_code = _parse_build_number(
      api_version_attr.value)

  new_elements = []

  idea_plugin = dom.documentElement

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

    idea_version_build_element = (api_version
                                  if args.include_product_code_in_stamp else
                                  api_version_without_product_code)

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
    changelog_text = _read_changelog(args.changelog_file)
    changelog_cdata = dom.createCDATASection(changelog_text)
    changelog_element.appendChild(changelog_cdata)
    new_elements.append(changelog_element)

  if args.plugin_id:
    if idea_plugin.getElementsByTagName("id"):
      raise ValueError("id element already in plugin.xml")
    id_element = dom.createElement("id")
    new_elements.append(id_element)
    id_text = dom.createTextNode(args.plugin_id)
    id_element.appendChild(id_text)

  if args.plugin_name:
    if idea_plugin.getElementsByTagName("name"):
      raise ValueError("name element already in plugin.xml")
    name_element = dom.createElement("name")
    new_elements.append(name_element)
    name_text = dom.createTextNode(args.plugin_name)
    name_element.appendChild(name_text)

  for new_element in new_elements:
    idea_plugin.appendChild(new_element)

  print dom.toxml()


if __name__ == "__main__":
  main()
