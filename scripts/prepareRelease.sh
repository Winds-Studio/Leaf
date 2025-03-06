#!/bin/bash
set -e

IS_EOL=true
IS_UNSUPPORTED=false

RELEASE_NOTES="release_notes.md"

# Move Leaf jar
mv ./leaf-1.21.4-${{ env.BUILD_NUMBER }}-mojmap.jar ./leaf-1.21.4-${{ env.BUILD_NUMBER }}.jar

# Branch name
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "Current branch: $CURRENT_BRANCH"

# Latest tag name
LATEST_TAG=$(git describe --tags --abbrev=0)
if [ -z "$LATEST_TAG" ]; then
  echo "No previous release found. Using initial commit."
  LATEST_TAG=$(git rev-list --max-parents=0 HEAD) # 初始提交
fi
echo "Latest tag: $LATEST_TAG"

# Commit of the latest tag
LAST_RELEASE_COMMIT=$(git rev-list -n 1 "$LATEST_TAG")
echo "Last release commit: $LAST_RELEASE_COMMIT"

# TODO: get commits log
#COMMIT_LOG=$(git log "$LAST_RELEASE_COMMIT"..HEAD --pretty=format:"- [\`%h\`](https://github.com/${ github.repository }/commit/%h) %s (%an)")
# if [ -z "$COMMIT_LOG" ]; then
#   COMMIT_LOG="No new commits since $LATEST_TAG."
# fi

# Release notes header
echo "" >> $RELEASE_NOTES

# Commits log
echo "### 📜 Commits:" >> $RELEASE_NOTES
echo "" >> $RELEASE_NOTES
echo "$COMMIT_LOG" >> $RELEASE_NOTES
echo "" >> $RELEASE_NOTES

echo "### 🔒 Checksums" >> $RELEASE_NOTES

# Get checksums
ARTIFACTS_DIR="."
if [ -d "$ARTIFACTS_DIR" ]; then
  for file in "$ARTIFACTS_DIR"/*.jar; do
    if [ -f "$file" ]; then
      MD5=$(md5sum "$file" | awk '{ print $1 }')
      SHA256=$(sha256sum "$file" | awk '{ print $1 }')
      FILENAME=$(basename "$file")

      echo "|           | $FILENAME |" >> $RELEASE_NOTES
      echo "| --------- | --------- |" >> $RELEASE_NOTES
      echo "| MD5       | $MD5      |" >> $RELEASE_NOTES
      echo "| SHA256    | $SHA256   |" >> $RELEASE_NOTES
    fi
  done
else
  echo "No artifacts found." >> $RELEASE_NOTES
fi

# EOL warning
if [ "$IS_EOL" = true ]; then
  echo "" >> $RELEASE_NOTES
  echo "> [!WARNING]" >> $RELEASE_NOTES
  echo "> This version of Leaf is end-of-life and will only receive critical bugfixes from upstream." >> $RELEASE_NOTES
  echo "> Update to latest version and gain better performance!" >> $RELEASE_NOTES
fi

# Unsupported warning
if [ "$IS_UNSUPPORTED" = true ]; then
  echo "" >> $RELEASE_NOTES
  echo "> [!CAUTION]" >> $RELEASE_NOTES
  echo "> This version of Leaf is unsupported and will not receive any bugfixes." >> $RELEASE_NOTES
  echo "> Use at your own risk!" >> $RELEASE_NOTES
fi

# Delete last tag
gh release delete ver-1.21.4 --cleanup-tag -y -R Winds-Studio/Leaf
