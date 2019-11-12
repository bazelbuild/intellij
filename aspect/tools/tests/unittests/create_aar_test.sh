#!/bin/bash
#

source googletest.sh || exit 1

ROOT=$PWD
OUTPUT_AAR_NAME="generated_aar"
EXPECTED_AAR="${TEST_SRCDIR}/google3/third_party/intellij/bazel/plugin/aspect/tools/tests/unittests/${OUTPUT_AAR_NAME}"
MANIFEST_FILE="${EXPECTED_AAR}/AndroidManifest.xml"
OUTPUT_AAR="${OUTPUT_AAR_NAME}.aar"
RESOURCE_FILES=("${EXPECTED_AAR}/res/values/colors.xml")
RESOURCE_ROOT="${EXPECTED_AAR}/res"

# Create Aar
source ${TEST_SRCDIR}/google3/third_party/intellij/bazel/plugin/aspect/tools/create_aar.sh ${OUTPUT_AAR} ${MANIFEST_FILE} ${RESOURCE_FILES} ${RESOURCE_ROOT}|| die "Failed to generate aar"

diff ${EXPECTED_AAR} ${ROOT}/${OUTPUT_AAR_NAME} || die "Generated aar is not the same as expected one"
