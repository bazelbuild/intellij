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
    raise RuntimeError("Incompatible top-level tags: '%s' vs. '%s'" %
                       (file_dom.documentElement.tagName,
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
