#!/usr/bin/env bash 
set -ex

function intellij::build() {
  local intellij_product="$1" # eg, "intellij-ue-2019.3"
  local shortcut=$(echo ${intellij_product} | sed 's/-.*$//g')
  local plugin_base=
  
  case $shortcut in
    intellij)
      plugin_base="ijwb"
      ;;
    clion)
      plugin_base="clwb"
      ;;
    android)
      plugin_base="aswb"
      ;;
    *)
      echo "Invalid product detected: $intellij_product"
      return 1
  esac

  # Do not change the code below
  local build_dir=/tmp/build_output

  bazel \
    --output_user_root=${build_dir} \
    build //${plugin_base}:${plugin_base}_bazel_zip \
    --define=ij_product=${intellij_product}

  local file="$(find ${build_dir} -name ${plugin_base}_bazel.zip)"
  cp -v ${file} ${build_dir}
}

intellij::build ${1:-"intellij-ue-latest"}
