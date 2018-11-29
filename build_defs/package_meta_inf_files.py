"""Adds a list of files into the META-INF directory of the passed deploy jar.
"""

import argparse
import shutil
import zipfile

try:
  from itertools import izip  # pylint: disable=g-importing-member,g-import-not-at-top
except ImportError:
  # Python 3.x already has a built-in `zip` that takes `izip`'s place.
  izip = zip

# Set to Jan 1 1980, the earliest date supported by zipfile
ZIP_DATE = (1980, 1, 1, 0, 0, 0)

parser = argparse.ArgumentParser()

parser.add_argument(
    "--deploy_jar",
    required=True,
    help="The deploy jar to modify.",)
parser.add_argument(
    "--output",
    required=True,
    help="The output file.",)
parser.add_argument(
    "meta_inf_files",
    nargs="+",
    help="Sequence of input file, final file name pairs",)


def pairwise(t):
  it = iter(t)
  return izip(it, it)


def main():
  args = parser.parse_args()

  shutil.copyfile(args.deploy_jar, args.output)
  output_jar = zipfile.ZipFile(args.output, "a")
  for meta_inf_file, name in pairwise(args.meta_inf_files):
    with open(meta_inf_file, "rb") as f:
      zip_info = zipfile.ZipInfo("META-INF/" + name, ZIP_DATE)
      output_jar.writestr(zip_info, f.read())

if __name__ == "__main__":
  main()
