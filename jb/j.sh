#!/bin/bash

# default install to /opt/jbang
install_jbang() {
  target_dir="${1:-/opt/}"
  jbang_version="${2:-0.110.1}"
  if [[ -d "${target_dir}/jbang" ]]; then
    echo "jbang already installed"
  else
    temp_dir=$(mktemp -d)
    temp_file="${temp_dir}/jbang.zip"
    # echo "https://github.com/jbangdev/jbang/releases/download/v${jbang_version}/jbang.zip"
    curl -v -L -o "$temp_file" "https://github.com/jbangdev/jbang/releases/download/v${jbang_version}/jbang.zip"
    unzip -d "$temp_dir" "$temp_file"
    if [[ ! -d "$target_dir" ]]; then
      mkdir -p "$target_dir"
    fi
    mv "$temp_dir/jbang" "$target_dir"
  fi
}

export PATH="/opt/jbang/bin:$PATH"

install_jbang "$@"

