#!/bin/bash
# This script will copy and zip manifest file and resource files into a AAR file
# which can be loaded by ASwB. It will be invoked via ctx.actions when collecting
# android ide info from a target.

ROOT=$PWD
# relative path to generated AAR file
OUTPUT_AAR=$1
# manifest file to be packed in AAR
MANIFEST_FILE=$2
# resource files to be packed in AAR
RESOURCE_FILES=($3)
# common parent of RESOURCE_FILES. RESOURCE_ROOT is used to decide RESOURCE_FILES
# hierarchy at copied destination.
RESOURCE_ROOT=$4
SCRATCH=$(basename "${OUTPUT_AAR}" .aar)

rm -rf "${SCRATCH}/"
mkdir -p "${SCRATCH}/"

# Copy AndroidManifest.xml
cp "${MANIFEST_FILE}" "${SCRATCH}/"

# Copy resource files
for RESOURCE_FILE in "${RESOURCE_FILES[@]}"
do
  DEST="${SCRATCH}/res${RESOURCE_FILE//$RESOURCE_ROOT/}"
  mkdir -p  $(dirname "${DEST}") && cp "${RESOURCE_FILE}" "${DEST}"
done

# R.txt is only used to optimize loading time for AAR. So it's not necessary.
# As minimum requirement, we only need to pack resource files and manifest file.
cd "${SCRATCH}" && zip -r --symlinks "${ROOT}/${OUTPUT_AAR}" ./*

