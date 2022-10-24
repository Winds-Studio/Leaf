#!/usr/bin/env bash

# file utilized in github actions to automatically update upstream

(
set -e
PS1="$"

current=$(cat gradle.properties | grep KeYiCommit | sed 's/KeYiCommit = //')
upstream=$(git ls-remote https://github.com/KeYiMC/KeYi | grep develop | cut -f 1)

if [ "$current" != "$upstream" ]; then
    sed -i 's/KeYiCommit = .*/KeYiCommit = '"$upstream"'/' gradle.properties
    {
      ./gradlew applyPatches --stacktrace && ./gradlew rebuildPatches --stacktrace
    } || exit

    git add .
    ./scripts/upstreamCommit.sh "$current"
fi

) || exit 1
