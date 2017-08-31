"""Appends XML elements specifying optional dependencies to a plugin XML file.
"""

import argparse
from itertools import izip
from xml.dom.minidom import parse

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

  if args.output:
    with file(args.output, "w") as f:
      f.write(dom.toxml(encoding="utf-8"))
  else:
    print dom.toxml(encoding="utf-8")


if __name__ == "__main__":
  main()
