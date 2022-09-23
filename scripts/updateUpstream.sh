#!/usr/bin/env bash

# file utilized in github actions to automatically update upstream

(
set -e
PS1="$"

current=$(cat gradle.properties | grep pufferfishCommit | sed 's/pufferfishCommit = //')
upstream=$(git ls-remote https://github.com/pufferfish-gg/Pufferfish | grep ver/1.19 | cut -f 1)

if [ "$current" != "$upstream" ]; then
    sed -i 's/pufferfishCommit = .*/pufferfishCommit = '"$upstream"'/' gradle.properties
    {
      ./gradlew applyPatches --stacktrace && ./gradlew rebuildPatches --stacktrace
    } || exit

    git add .
    ./scripts/upstreamCommit.sh "$current"
fi

) || exit 1
