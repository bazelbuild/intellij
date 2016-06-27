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

"""Merges multiple xml files with the same top element tags into a single file.
"""

import sys
from xml.dom.minidom import parse


def AppendFileToTree(filepath, tree):
  """Reads XML from a file and appends XML content to the tree.

  Root elements for both trees must have the same tag.

  Args:
    filepath: Path to the file containing XML specification.
    tree: Tree to add content to.

  Raises:
    RuntimeError: The top-level XML tags are incompatible
  """

  file_dom = parse(filepath)

  if file_dom.documentElement.tagName != tree.documentElement.tagName:
    raise RuntimeError("Incompatible top-level tags: '%s' vs. '%s'"
                       % (file_dom.documentElement.tagName,
                          tree.documentElement.tagName))

  for node in file_dom.documentElement.childNodes:
    tree.documentElement.appendChild(tree.importNode(node, True))


if __name__ == "__main__":
  if len(sys.argv) < 2:
    print "Need xml filename(s) to be checked as parameter"
    sys.exit(2)

  dom = parse(sys.argv[1])
  for filename in sys.argv[2:]:
    AppendFileToTree(filename, dom)

  print dom.toxml()
