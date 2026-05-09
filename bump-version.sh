#!/usr/bin/env bash
# bump-version.sh — bump version in version.properties and create a git tag.
#
# Usage:
#   ./bump-version.sh patch   # 1.0.1 -> 1.0.2
#   ./bump-version.sh minor   # 1.0.1 -> 1.1.0
#   ./bump-version.sh major   # 1.0.1 -> 2.0.0
#   ./bump-version.sh 1.2.3   # set exact version

set -euo pipefail

PROPS="version.properties"

if [[ ! -f "$PROPS" ]]; then
  echo "VERSION_NAME=1.0.0" > "$PROPS"
  echo "VERSION_CODE=1"     >> "$PROPS"
fi

CURRENT_NAME=$(grep "^VERSION_NAME=" "$PROPS" | cut -d'=' -f2)
CURRENT_CODE=$(grep "^VERSION_CODE=" "$PROPS" | cut -d'=' -f2)

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"

case "${1:-patch}" in
  patch)  NEW_NAME="$MAJOR.$MINOR.$((PATCH + 1))" ;;
  minor)  NEW_NAME="$MAJOR.$((MINOR + 1)).0" ;;
  major)  NEW_NAME="$((MAJOR + 1)).0.0" ;;
  *.*.*)  NEW_NAME="$1" ;;                       # explicit version
  *)      echo "Usage: $0 {major|minor|patch|x.y.z}"; exit 1 ;;
esac

NEW_CODE=$((CURRENT_CODE + 1))

# Write back
sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$NEW_NAME/" "$PROPS"
sed -i "s/^VERSION_CODE=.*/VERSION_CODE=$NEW_CODE/" "$PROPS"

echo "Version bumped: $CURRENT_NAME (code $CURRENT_CODE) -> $NEW_NAME (code $NEW_CODE)"
echo ""
echo "Next steps:"
echo "  git add version.properties app/build.gradle.kts"
echo "  git commit -m \"release: v$NEW_NAME\""
echo "  git tag v$NEW_NAME"
echo "  git push origin master --tags"
