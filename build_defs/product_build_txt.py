#!/usr/bin/python3
#
# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Produces a product-build.txt with the product build."""

import argparse
import re
from xml.dom.minidom import parseString  # pylint: disable=g-importing-member
import zipfile

parser = argparse.ArgumentParser()

parser.add_argument(
    "--application_info_jar",
    help="The jar file containing the application info xml",
    required=True,)
parser.add_argument(
    "--application_info_name",
    help="A .txt file containing the application info xml name",
    required=True,)


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
    build_number_attr = attrs.get("number")

  if not build_number_attr:
    raise ValueError("Could not find build number in application info")

  build_number, _ = _parse_build_number(build_number_attr.value)

  print(build_number)  # pylint: disable=superfluous-parens


if __name__ == "__main__":
  main()
