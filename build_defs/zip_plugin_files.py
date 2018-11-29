"""Packages plugin files into a zip archive."""

import argparse
import time
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

  # zipfile cannot be coaxed into putting a custom timestamp into the zip files,
  # and we cannot modify the timestamp of the file itself (because it's a CAS
  # entry on Forge). Therefore, we replace time.localtime().
  time.localtime = lambda _: [2000, 1, 1, 0, 0, 0, 0, 0, 0]

  outfile = zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED)
  for exec_path, zip_path in pairwise(args.files_to_zip):
    outfile.write(exec_path, zip_path)
  outfile.close()


if __name__ == "__main__":
  main()
