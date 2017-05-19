"""Adds a list of files into the META-INF directory of the passed deploy jar.
"""

import argparse
from itertools import izip
import shutil
import zipfile

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
    with file(meta_inf_file) as f:
      zip_info = zipfile.ZipInfo("META-INF/" + name, ZIP_DATE)
      output_jar.writestr(zip_info, f.read())

if __name__ == "__main__":
  main()
