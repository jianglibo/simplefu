#!/bin/bash

if command -v jbang; then
  echo "jbang already installed"
else
  echo "Installing jbang"
  curl -Ls https://sh.jbang.dev | bash -s - app setup
fi