#!/bin/bash
set -e

LATEST_TAG=$(git describe --tags --abbrev=0)
echo "Latest tag: $LATEST_TAG"

LAST_RELEASE_COMMIT=$(git rev-list -n 1 "$LATEST_TAG")
echo "Last release commit: $LAST_RELEASE_COMMIT"
