"""Merges multiple xml files with the same top element tags into a single file.
"""

import argparse
import sys
from xml.dom.minidom import parse

parser = argparse.ArgumentParser()

parser.add_argument(
    "--output",
    help="The file to output to. If none, prints to stdout.",
    required=False,)

parser.add_argument(
    "xmls",
    nargs="+",
    help="The xml files to merge",)


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
  args = parser.parse_args()
  if not args.xmls:
    sys.exit(2)

  dom = parse(args.xmls[0])
  for filename in args.xmls[1:]:
    AppendFileToTree(filename, dom)

  if args.output:
    with file(args.output, "w") as f:
      f.write(dom.toxml())
  else:
    print dom.toxml()
