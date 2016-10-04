#!/bin/bash

# Copyright 2016 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Use this to create a bug report for IntelliJ plugin bugs.
#
# Usage: create_bugreport.sh
#

# For all jars in specified plugin directory, extracts plugin.xml
# and copies to the specified output directory.
# args:
#   - IJ plugin directory
#   - output directory
attach_plugin_xmls() {
  if [ ! -e $1 ]; then return; fi
  pushd $1 >/dev/null
  jars=(*.jar)
  for jar in ${jars[@]}; do
    plugin=${jar%.jar}
    if [ -e $jar ]; then
      mkdir -p $2
      unzip -p $jar "META-INF/plugin.xml" > "$2/${plugin}.xml"
      [ $? -eq 0 ] || { exit 1; }
    fi
  done
  popd >/dev/null
}

files=""
tmp_dir=$(mktemp -d)
output_name=intellij-bug-report-$USER
tar_dir=$tmp_dir/$output_name
output_file=${output_name}.tar.gz

mkdir -p tar_dir
[ $? -eq 0 ] || { exit 1; }

# Attach process information
process_info_dir=$tar_dir/process_info
mkdir -p $process_info_dir

pids=$(jps | awk '/[0-9]+ (Main)$/ { print $1 }')
ts=$(date +%H%M%S)
if [ -z "$pids" ]; then
  echo "Warn: Could not find any IntelliJ processes."
fi
for pid in $pids
do
  stack_file=$process_info_dir/stack_${pid}_${ts}.txt
  mem_file=$process_info_dir/mem_${pid}_${ts}.txt
  capacity_file=$process_info_dir/capacity_${pid}_${ts}.txt
  cmd_file=$process_info_dir/cmd_${pid}_${ts}.txt
  uptime_file=$process_info_dir/uptime_${pid}_${ts}.txt
  jps -v | grep $pid > $cmd_file
  jstack $pid > $stack_file
  jstat -gc $pid > $mem_file
  jstat -gccapacity $pid > $capacity_file
  ps -p $pid -o etime= > $uptime_file
done

# Copy core dumps
mkdir -p $tar_dir/jvm-dumps
cp -p $HOME/java_error_in_* $tar_dir/jvm-dumps 2>/dev/null

# Copy vmoptions files
mkdir -p $tar_dir
cp -p $HOME/*.vmoptions $tar_dir 2>/dev/null

# Copy details from IJ log directories
dir_names=(
  '.IntelliJIdea*'
  '.IdeaIC*'
  '.AndroidStudio*'
  '.CLion*'
)
# other product codes, to add if we end up supporting them:
# PhpStorm, WebStorm, RubyMine, PyCharm, WebIde, AppCode, DataGrip

pushd $HOME >/dev/null
for log_dirs in ${dir_names[@]}; do
  for log_dir in $log_dirs ; do
    if [ ! -e $log_dir ]; then
      continue
    fi
    product=${log_dir:1}
    mkdir ${tar_dir}/${product}
    pushd ${tar_dir}/${product} >/dev/null

    # Attach user's log directories
    if [ -d ${HOME}/${log_dir}/system/log ]; then
      mkdir -p "system/log"
      cp -r "${HOME}/${log_dir}/system/log" "system"
      [ $? -eq 0 ] || { exit 1; }
    fi

    # Attach product version
    ij_home=$(<"${HOME}/${log_dir}/system/.home")
    cp "${ij_home}/build.txt" "version"

    # Attach plugin.xmls
    attach_plugin_xmls "${HOME}/${log_dir}/system/plugins" "${tar_dir}/${product}/system/plugins"
    attach_plugin_xmls "${HOME}/${log_dir}/config/plugins" "${tar_dir}/${product}/config/plugins"

    # copy vmoptions
    mkdir -p vmoptions/home
    cp -p ${HOME}/${log_dir}/*.vmoptions vmoptions/home 2>/dev/null
    mkdir -p vmoptions/installation
    cp -p ${ij_home}/bin/*.vmoptions vmoptions/installation 2>/dev/null

    popd >/dev/null
  done
done
popd >/dev/null

# Attach user's .blazerc
if [ -f $HOME/.blazerc ]; then
  cp $HOME/.blazerc $tar_dir/.blazerc
fi

pushd $tmp_dir >/dev/null
tar -chzf $output_file $output_name
[ $? -eq 0 ] || { exit 1; }
popd >/dev/null

tar_file=$tmp_dir/$output_file
echo "Bug report produced in: $tar_file"
echo "Path has been copied to clipboard."
echo -n $tar_file | xclip -selection primary
echo -n $tar_file | xclip -selection clipboard

