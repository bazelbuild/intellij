#!/usr/bin/python
"""Packages plugin files into a zip archive."""

import argparse
import os
import stat
import zipfile

try:
  from itertools import izip  # pylint: disable=g-importing-member,g-import-not-at-top
except ImportError:
  # Python 3.x already has a built-in `zip` that takes `izip`'s place.
  izip = zip

parser = argparse.ArgumentParser()

parser.add_argument("--output", help="The output filename.", required=True)
parser.add_argument(
    "files_to_zip", nargs="+", help="Sequence of exec_path, zip_path... pairs")


def pairwise(t):
  it = iter(t)
  return izip(it, it)


def main():
  args = parser.parse_args()
  with zipfile.ZipFile(args.output, "w") as outfile:
    for exec_path, zip_path in pairwise(args.files_to_zip):
      with open(exec_path) as input_file:
        zipinfo = zipfile.ZipInfo(zip_path, (2000, 1, 1, 0, 0, 0))
        filemode = stat.S_IMODE(os.fstat(input_file.fileno()).st_mode)
        zipinfo.external_attr = filemode << 16
        outfile.writestr(zipinfo, input_file.read(), zipfile.ZIP_DEFLATED)

if __name__ == "__main__":
  main()
