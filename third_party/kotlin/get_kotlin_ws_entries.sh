#!/usr/bin/env bash

set -e

# Process an Intelij Kotlin plugin link into a valid workspace entry.
function process_workspace_link() {
    local url=$(curl -w "%{url_effective}\n" -I -L -s -S $2 -o /dev/null);

    if [[  "${url}" =~ (.*)/(.*\.zip)\?.* ]]; then
        local fn=${BASH_REMATCH[2]};
        url="${BASH_REMATCH[1]}/${fn}"
        local dl_path=/tmp/${fn}
        curl -sS -o ${dl_path} ${url}

        local sha=$(shasum -a 256 /tmp/${fn})
        if [[  "${sha}" =~ (.*)[[:space:]][[:space:]].* ]]; then
            sha=${BASH_REMATCH[1]};
        fi

        cat << EOF
new_http_archive(
    name = "${1}",
    build_file_content = KOTLIN_BUILD_FILE_CONTENT,
    url = "${url}",
    sha256 = "${sha}"
)
EOF
    rm ${dl_path}
    fi
}

process_workspace_link "kotlin_2017_2" https://plugins.jetbrains.com/plugin/download?updateId=42314
process_workspace_link "kotlin_2017_3" https://plugins.jetbrains.com/plugin/download?updateId=42315
