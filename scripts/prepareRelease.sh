#!/bin/bash
set -e

LATEST_TAG=$(git describe --tags $(git rev-list --tags --max-count=1))
echo "Latest tag: $LATEST_TAG"
