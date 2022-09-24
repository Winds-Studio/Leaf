#!/usr/bin/env bash

# file utilized in github actions to automatically update upstream

(
set -e
PS1="$"

current=$(cat gradle.properties | grep MiraiCommit | sed 's/MiraiCommit = //')
upstream=$(git ls-remote https://github.com/etil2jz/Mirai | grep ver/1.19.2 | cut -f 1)

if [ "$current" != "$upstream" ]; then
    sed -i 's/MiraiCommit = .*/MiraiCommit = '"$upstream"'/' gradle.properties
    {
      ./gradlew applyPatches --stacktrace && ./gradlew rebuildPatches --stacktrace
    } || exit

    git add .
    ./scripts/upstreamCommit.sh "$current"
fi

) || exit 1
