"""Appends XML elements specifying optional dependencies to a plugin XML file.
"""

import argparse
import sys
from xml.dom.minidom import parse  # pylint: disable=g-importing-member

try:
  from itertools import izip  # pylint: disable=g-importing-member,g-import-not-at-top
except ImportError:
  # Python 3.x already has a built-in `zip` that takes `izip`'s place.
  izip = zip

parser = argparse.ArgumentParser()

parser.add_argument(
    "--plugin_xml", help="The main plugin xml file", required=True)
parser.add_argument("--output", help="The output file.")
parser.add_argument(
    "optional_xml_files",
    nargs="+",
    help="Sequence of module, module xml... pairs")


def pairwise(t):
  it = iter(t)
  return izip(it, it)


def main():

  args = parser.parse_args()
  dom = parse(args.plugin_xml)

  plugin_xml = dom.documentElement

  for module, optional_xml in pairwise(args.optional_xml_files):
    depends_element = dom.createElement("depends")
    depends_element.setAttribute("optional", "true")
    depends_element.setAttribute("config-file", optional_xml)
    depends_element.appendChild(dom.createTextNode(module))
    plugin_xml.appendChild(depends_element)
    plugin_xml.appendChild(dom.createTextNode("\n"))

  if args.output:
    with open(args.output, "wb") as f:
      f.write(dom.toxml(encoding="utf-8"))
  else:
    if hasattr(sys.stdout, "buffer"):
      sys.stdout.buffer.write(dom.toxml(encoding="utf-8"))
    else:
      # Python 2.x has no sys.stdout.buffer, but `print` still accepts byte
      # strings.
      print(dom.toxml(encoding="utf-8"))  # pylint: disable=superfluous-parens


if __name__ == "__main__":
  main()
