#!/usr/bin/env bash

# requires curl & jq
# Credit: https://github.com/PurpurMC/Purpur

# Usage:
# upstreamCommit --paper HASH --purpur HASH --leaves HASH
# flag: --paper HASH - (Optional) the commit hash to use for comparing commits between paper (PaperMC/Paper/compare/HASH...HEAD)
# flag: --purpur HASH - the commit hash to use for comparing commits between purpur (PurpurMC/Purpur/compare/HASH...HEAD)
# flag: --leaves HASH - the commit hash to use for comparing commits between leaves (LeavesMC/Leaves/compare/HASH...HEAD)

function getCommits() {
    curl -H "Accept: application/vnd.github.v3+json" https://api.github.com/repos/"$1"/compare/"$2"..."$3" | jq -r '.commits[] | "'"$1"'@\(.sha[:8]) \(.commit.message | split("\r\n")[0] | split("\n")[0])" | sub("\\[ci( |-)skip]"; "[ci/skip]")'
}

(
set -e
PS1="$"

paperHash=$(git diff gradle.properties | awk '/^-paperCommit =/{print $NF}')
purpurHash=""
leavesHash=""

# Useless params standardize
# TEMP=$(getopt --long paper:,purpur:,leaves: -o "" -- "$@")
# eval set -- "$TEMP"
while true; do
    case "$1" in
        --paper)
            paperHash="$2"
            shift 2
            ;;
        --purpur)
            purpurHash="$2"
            shift 2
            ;;
        --leaves)
            leavesHash="$2"
            shift 2
            ;;
        *)
            break
            ;;
    esac
done

paper=""
purpur=""
leaves=""
updated=""
logsuffix=""

# Paper updates
if [ -n "$paperHash" ]; then
    newHash=$(git diff gradle.properties | awk '/^+paperCommit =/{print $NF}')
    paper=$(getCommits "PaperMC/Paper" "$paperHash" $(echo $newHash | grep . -q && echo $newHash || echo "main")) # Update this on every version update

    # Updates found
    if [ -n "$paper" ]; then
        updated="Paper"
        logsuffix="$logsuffix\n\nPaper Changes:\n$paper"
    fi
fi

# Purpur updates
if [ -n "$purpurHash" ]; then
    purpur=$(getCommits "PurpurMC/Purpur" "$purpurHash" "ver/1.21.8") # Update this on every version update

    # Updates found
    if [ -n "$purpur" ]; then
        updated="${updated:+$updated/}Purpur"
        logsuffix="$logsuffix\n\nPurpur Changes:\n$purpur"
    fi
fi

# Leaves updates
if [ -n "$leavesHash" ]; then
    leaves=$(getCommits "LeavesMC/Leaves" "$leavesHash" "HEAD")

    # Updates found
    if [ -n "$leaves" ]; then
        updated="${updated:+$updated/}Leaves"
        logsuffix="$logsuffix\n\nLeaves Changes:\n$leaves"
    fi
fi

disclaimer="Upstream has released updates that appear to apply and compile correctly"
log="Updated Upstream ($updated)\n\n${disclaimer}${logsuffix}"

git add gradle.properties

echo -e "$log" | git commit -F -

) || exit 1
