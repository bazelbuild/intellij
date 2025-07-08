#
# Skeleton of a script to release the Bazel plugin to the external JetBrains marketplace.
# The script downloads the source code from the GitHub repo,
# builds it with Bazel then uploads it to JetBrains marketplace.
#

BAZEL="/usr/bin/bazel"
GIT="/usr/bin/git"

if [[ ! -x "${BAZEL}" ]]
then
  echo "error: cannot find bazel, please install via \`sudo apt install bazel\`"
  exit 1
fi

if [[ ! -x "${GIT}" ]]
then
  echo "error: cannot find git, please install via \`sudo apt install git\`"
  exit 1
fi

product=$1 # clwb or ijwb
release_branch=$2
version=$3 # oss-latest-stable or oss-oldest-stable
repo_channel=$4 # beta or Stable
rc_num=$5

local product_full_name build_target test_target

case "$product" in
  clwb)
    product_full_name="clion"
    build_target="//clwb:clwb_bazel_zip"
    test_target="//:clwb_tests"
    ;;
  ijwb)
    product_full_name="intellij-ue"
    build_target="//ijwb:ijwb_bazel_zip"
    test_target="//:ijwb_ue_tests"
    ;;
esac


# Clone the GitHub repo
git clone https://github.com/bazelbuild/intellij
pushd "intellij" >/dev/null
git checkout "$release_branch"

"${BAZEL}" clean --expunge

# Run the plugin tests for this version
"${BAZEL}" test "${test_target}" \
--define=ij_product="${product_full_name}"-"${version}"
if [[ "$?" -ne "0" ]]
then
  echo "error: plugin tests failed"
  exit 1
fi

# Set the plugin version
plugin_version="${release_branch:1}.0.${rc_num}"
echo "VERSION = \"$plugin_version\"" > version.bzl

# Build the plugin
"${BAZEL}" build "${build_target}" \
--define=ij_product="${product_full_name}"-"${version}"

if [[ "$?" -ne "0" ]]
then
  echo "error: plugin build failed"
  exit 1
fi

# Upload the plugin to JetBrains marketplace (upload token maybe needed)
