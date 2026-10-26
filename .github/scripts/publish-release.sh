#!/usr/bin/env bash
set -euo pipefail

channel=$1
artifact=$2
manifest=${3:-}
: "${GITHUB_SHA:?}" "${GITHUB_REPOSITORY:?}"
immutable_tag="build-${channel}-${GITHUB_SHA}"
artifact_name=$(basename "$artifact")
release_tmp=$(mktemp -d "${RUNNER_TEMP:-/tmp}/androsaver-release.XXXXXX")
trap 'rm -rf "$release_tmp"' EXIT
assets=("$artifact")
if [ -n "$manifest" ]; then assets+=("$manifest"); fi
prerelease=false
if [ "$channel" = dev-channel ]; then prerelease=true; fi

verify_assets() {
  local tag=$1
  local attempt
  mkdir -p "$release_tmp/$tag"
  for attempt in 1 2 3 4 5; do
    if gh release download "$tag" --repo "$GITHUB_REPOSITORY" \
      --pattern "$artifact_name" --dir "$release_tmp/$tag" --clobber; then break; fi
    if [ "$attempt" = 5 ]; then return 1; fi
    sleep 5
  done
  cmp "$artifact" "$release_tmp/$tag/$artifact_name"
  if [ -n "$manifest" ]; then
    gh release download "$tag" --repo "$GITHUB_REPOSITORY" \
      --pattern version.json --dir "$release_tmp/$tag" --clobber
    cmp "$manifest" "$release_tmp/$tag/version.json"
    test "$(jq -r .sha256 "$release_tmp/$tag/version.json")" = \
      "$(sha256sum "$release_tmp/$tag/$artifact_name" | cut -d' ' -f1)"
    test "$(jq -r .sizeBytes "$release_tmp/$tag/version.json")" = \
      "$(stat -c '%s' "$release_tmp/$tag/$artifact_name")"
  fi
}

# Stage and verify the commit-specific assets before making them public.
if gh release view "$immutable_tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  if [ "$(gh release view "$immutable_tag" --repo "$GITHUB_REPOSITORY" --json isDraft --jq .isDraft)" = true ]; then
    gh release upload "$immutable_tag" "${assets[@]}" --repo "$GITHUB_REPOSITORY" --clobber
  fi
else
  gh release create "$immutable_tag" "${assets[@]}" --repo "$GITHUB_REPOSITORY" \
    --target "$GITHUB_SHA" --title "$immutable_tag" --draft --prerelease="$prerelease" --latest=false
fi
verify_assets "$immutable_tag"
gh release edit "$immutable_tag" --repo "$GITHUB_REPOSITORY" --draft=false --latest=false
test "$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$immutable_tag" --jq .object.sha)" = "$GITHUB_SHA"

# Keep rolling URLs for older installed clients. New clients use immutableApkUrl,
# so an overlapping rolling upload cannot change the APK named by their manifest.
if gh release view "$channel" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  gh release upload "$channel" "${assets[@]}" --repo "$GITHUB_REPOSITORY" --clobber
else
  gh release create "$channel" "${assets[@]}" --repo "$GITHUB_REPOSITORY" \
    --target "$GITHUB_SHA" --title "$channel" --prerelease="$prerelease" --latest=false
fi
verify_assets "$channel"
# Only move an existing channel tag after downloaded assets have passed verification.
git push origin "${GITHUB_SHA}:refs/tags/$channel" --force
test "$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$channel" --jq .object.sha)" = "$GITHUB_SHA"
latest=false
if [ "$channel" = stable ]; then latest=true; fi
gh release edit "$channel" --repo "$GITHUB_REPOSITORY" --prerelease="$prerelease" --latest="$latest"
