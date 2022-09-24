#!/usr/bin/env bash

# file utilized in github actions to automatically update upstream

(
set -e
PS1="$"

current=$(cat gradle.properties | grep PrismarineCommit | sed 's/PrismarineCommit = //')
upstream=$(git ls-remote https://github.com/PrismarineTeam/Prismarine | grep ver/1.19.2 | cut -f 1)

if [ "$current" != "$upstream" ]; then
    sed -i 's/PrismarineCommit = .*/PrismarineCommit = '"$upstream"'/' gradle.properties
    {
      ./gradlew applyPatches --stacktrace && ./gradlew rebuildPatches --stacktrace
    } || exit

    git add .
    ./scripts/upstreamCommit.sh "$current"
fi

) || exit 1
