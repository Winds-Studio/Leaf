#!/bin/bash
set -e

IS_EOL=false
IS_UNSUPPORTED=false
IS_DEV=false

JAR_NAME="leaf-1.21.4"
CURRENT_TAG="ver-1.21.4"
RELEASE_NOTES="release_notes.md"

# Rename Leaf jar
mv ./$JAR_NAME-${BUILD_NUMBER}-mojmap.jar ./$JAR_NAME-${BUILD_NUMBER}.jar

# Branch name
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "✨Current branch: $CURRENT_BRANCH"

# Latest tag name
LATEST_TAG=$(git describe --tags --abbrev=0)
if [ -z $LATEST_TAG ]; then
  LATEST_TAG=$(git rev-list --max-parents=0 HEAD)
  echo "⚠️No previous release found. Using initial commit."
else
  echo "✨Latest tag: $LATEST_TAG"
fi

# Commit of the latest tag
LAST_RELEASE_COMMIT=$(git rev-list -n 1 $LATEST_TAG)
echo "✨Last release commit: $LAST_RELEASE_COMMIT"

# Commits log
COMMIT_LOG=$(git log $LAST_RELEASE_COMMIT..HEAD --pretty=format:"- [\`%h\`](https://github.com/${GITHUB_REPO}/commit/%H) %s (%an)")
if [ -z $COMMIT_LOG ]; then
  COMMIT_LOG="⚠️No new commits since $LATEST_TAG."
else
  echo "✅Commits log generated"
fi

# Release notes header
echo "" >> $RELEASE_NOTES

# Commits log
{
  echo "### 📜 Commits:"
  echo ""
  echo "$COMMIT_LOG"
  echo ""
  echo "### 🔒 Checksums"
} >> $RELEASE_NOTES

# Get checksums
file="./$JAR_NAME-${BUILD_NUMBER}.jar"
if [ -f $file ]; then
  MD5=$(md5sum $file | awk '{ print $1 }')
  SHA256=$(sha256sum $file | awk '{ print $1 }')
  FILENAME=$(basename $file)

  {
    echo "|           | $FILENAME |"
    echo "| --------- | --------- |"
    echo "| MD5       | $MD5      |"
    echo "| SHA256    | $SHA256   |"
  } >> $RELEASE_NOTES

  echo "🔒Checksums calculated:"
  echo "   MD5: $MD5"
  echo "   SHA256: $SHA256"
else
  echo "⚠️No artifacts found." >> $RELEASE_NOTES
fi

# EOL warning
if [ $IS_EOL = true ]; then
  {
    echo ""
    echo "> [!WARNING]"
    echo "> This version of Leaf is end-of-life and will only receive critical bugfixes from upstream."
    echo "> Update to latest version and gain better performance!"
  } >> $RELEASE_NOTES
fi

# Unsupported warning
if [ $IS_UNSUPPORTED = true ]; then
  {
    echo ""
    echo "> [!CAUTION]"
    echo "> This version of Leaf is unsupported and will not receive any bugfixes."
    echo "> Use at your own risk!"
  } >> $RELEASE_NOTES
fi

# Dev build warning
if [ $IS_DEV = true ]; then
  {
    echo ""
    echo "> [!WARNING]"
    echo "> This is the early dev build, only for testing usage."
    echo "> Do not use in the production environment!"
  } >> $RELEASE_NOTES
fi

# Delete current release tag
if git show-ref --tags $CURRENT_TAG --quiet; then
  {
    gh release delete $CURRENT_TAG --cleanup-tag -y -R "${GITHUB_REPO}"
  }
fi
echo "🚀Ready for release"
