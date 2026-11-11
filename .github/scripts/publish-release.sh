#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_SHA:?}" "${GITHUB_REPOSITORY:?}"

fail() { echo "Release error: $*" >&2; exit 1; }

# Read the built package, not Git history or a second version formula.
apk_identity() {
  local apk=$1
  apk_code=$(apkanalyzer manifest version-code "$apk")
  apk_version=$(apkanalyzer manifest version-name "$apk")
  test "$(apkanalyzer manifest application-id "$apk")" = com.androsaver || fail "Unexpected APK package"
  [[ "$apk_code" =~ ^[1-9][0-9]*$ ]] && [ "$apk_code" -le 2100000000 ] || fail "Invalid APK version code"
  [ -n "$apk_version" ] || fail "Missing APK version name"
}

write_manifest() {
  local channel=$1 apk=$2 output=$3
  apk_identity "$apk"
  jq -n --argjson code "$apk_code" --arg version "$apk_version" \
    --arg base "https://github.com/$GITHUB_REPOSITORY/releases/download" \
    --arg channel "$channel" --arg commit "$GITHUB_SHA" \
    --arg sha "$(sha256sum "$apk" | cut -d' ' -f1)" --argjson size "$(stat -c '%s' "$apk")" \
    '{versionCode:$code, versionName:$version, apkUrl:($base+"/"+$channel+"/androsaver.apk"),
      immutableApkUrl:($base+"/build-"+$channel+"-"+$commit+"/androsaver.apk"),
      sha256:$sha, sizeBytes:$size}' > "$output"
}

if [ "${1:-}" = --manifest ]; then
  [ "$#" = 4 ] || fail "Usage: --manifest CHANNEL APK OUTPUT"
  write_manifest "$2" "$3" "$4"
  exit 0
fi

channel=${1:?}
artifact=${2:?}
manifest=${3:-}
case "$channel" in dev-channel|stable|playstore-stable) ;; *) fail "Unknown release channel" ;; esac
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid source commit"
immutable_tag="build-${channel}-${GITHUB_SHA}"
artifact_name=$(basename "$artifact")
release_tmp=$(mktemp -d "${RUNNER_TEMP:-/tmp}/androsaver-release.XXXXXX")
trap 'rm -rf "$release_tmp"' EXIT
prerelease=false
if [ "$channel" = dev-channel ]; then prerelease=true; fi

# Git transport avoids the REST ref cache immediately after a push. Handle peeled
# annotated tags as well. A transport error must never look like an absent tag.
remote_tag() {
  local refs status
  if refs=$(git ls-remote --exit-code origin "refs/tags/$1" "refs/tags/$1^{}"); then
    printf '%s\n' "$refs" | awk -v peel="${2:-true}" 'NR == 1 { sha=$1 } peel == "true" && /\^\{\}$/ { sha=$1 } END { print sha }'
  else
    status=$?
    [ "$status" = 2 ] || return "$status"
  fi
}

verify_tag() {
  local attempt actual
  for attempt in 1 2 3 4 5; do
    actual=$(remote_tag "$1")
    if [ "$actual" = "$GITHUB_SHA" ]; then return; fi
    if [ "$attempt" != 5 ]; then sleep 2; fi
  done
  fail "Tag $1 did not resolve to $GITHUB_SHA (last observed: ${actual:-absent})"
}

# The workflow serializes all publishing runs. An old rerun must not mutate any
# channel asset, even if its immutable release was already published successfully.
previous_tag=$(remote_tag "$channel")
if [ -n "$previous_tag" ] && [ "$previous_tag" != "$GITHUB_SHA" ]; then
  git fetch --no-tags origin "$previous_tag"
  if git merge-base --is-ancestor "$previous_tag" "$GITHUB_SHA"; then
    :
  else
    status=$?
    if [ "$status" = 1 ]; then
      echo "Skipping obsolete/divergent release: $channel already points to $previous_tag"
      exit 0
    fi
    fail "Could not establish channel ancestry"
  fi
fi

if [ -n "$manifest" ]; then
  [ "$artifact_name" = androsaver.apk ] || fail "Unexpected APK filename"
  [ "$(basename "$manifest")" = version.json ] || fail "Unexpected update manifest filename"
  evidence_name=version.json
else
  [ "$artifact_name" = androsaver.aab ] || fail "A version manifest is required for APK releases"
  # AAB releases also need durable evidence to safely reuse a published build.
  evidence_name='artifact-integrity.json'
  manifest="$release_tmp/$evidence_name"
  jq -n --arg commit "$GITHUB_SHA" --arg sha "$(sha256sum "$artifact" | cut -d' ' -f1)" \
    --argjson size "$(stat -c '%s' "$artifact")" \
    '{commit:$commit, sha256:$sha, sizeBytes:$size}' > "$manifest"
fi

validate_payload() {
  local binary=$1 evidence=$2
  jq -e --arg sha "$(sha256sum "$binary" | cut -d' ' -f1)" \
    --argjson size "$(stat -c '%s' "$binary")" \
    '.sha256 == $sha and .sizeBytes == $size' "$evidence" >/dev/null || fail "Artifact checksum/size mismatch"
  if [ "$evidence_name" = version.json ]; then
    apk_identity "$binary"
    jq -e --argjson code "$apk_code" --arg version "$apk_version" \
      --arg url "https://github.com/$GITHUB_REPOSITORY/releases/download/$immutable_tag/androsaver.apk" \
      --arg rolling "https://github.com/$GITHUB_REPOSITORY/releases/download/$channel/androsaver.apk" \
      '.versionCode == $code and .versionName == $version and .immutableApkUrl == $url and .apkUrl == $rolling' \
      "$evidence" >/dev/null || fail "APK identity and update manifest disagree"
  else
    jq -e --arg commit "$GITHUB_SHA" '.commit == $commit' "$evidence" >/dev/null || fail "Wrong AAB source commit"
  fi
}

validate_payload "$artifact" "$manifest"

download_payload() {
  local tag=$1 directory=$2 attempt
  mkdir -p "$directory"
  for attempt in 1 2 3 4 5; do
    if gh release download "$tag" --repo "$GITHUB_REPOSITORY" --pattern "$artifact_name" --dir "$directory" --clobber &&
       gh release download "$tag" --repo "$GITHUB_REPOSITORY" --pattern "$evidence_name" --dir "$directory" --clobber; then
      return 0
    fi
    if [ "$attempt" != 5 ]; then sleep 2; fi
  done
  fail "Could not download complete release $tag"
}

immutable_ref=$(remote_tag "$immutable_tag")
[ -z "$immutable_ref" ] || [ "$immutable_ref" = "$GITHUB_SHA" ] || fail "Immutable tag belongs to another commit"
if draft=$(gh release view "$immutable_tag" --repo "$GITHUB_REPOSITORY" --json isDraft --jq .isDraft); then
  if [ "$draft" = false ]; then
    verify_tag "$immutable_tag"
    # A rebuild need not be byte-identical. The first published, validated build
    # for this commit is authoritative and must never be overwritten.
    download_payload "$immutable_tag" "$release_tmp/published"
    validate_payload "$release_tmp/published/$artifact_name" "$release_tmp/published/$evidence_name"
    artifact="$release_tmp/published/$artifact_name"
    manifest="$release_tmp/published/$evidence_name"
  elif [ "$draft" = true ]; then
    gh release upload "$immutable_tag" "$artifact" "$manifest" --repo "$GITHUB_REPOSITORY" --clobber
  else
    fail "Invalid release state for $immutable_tag"
  fi
else
  gh release create "$immutable_tag" "$artifact" "$manifest" --repo "$GITHUB_REPOSITORY" \
    --target "$GITHUB_SHA" --title "$immutable_tag" --draft --prerelease="$prerelease" --latest=false
fi

verify_assets() {
  local tag=$1
  download_payload "$tag" "$release_tmp/verify-$tag"
  validate_payload "$release_tmp/verify-$tag/$artifact_name" "$release_tmp/verify-$tag/$evidence_name"
  cmp "$artifact" "$release_tmp/verify-$tag/$artifact_name"
  cmp "$manifest" "$release_tmp/verify-$tag/$evidence_name"
}

verify_assets "$immutable_tag"
gh release edit "$immutable_tag" --repo "$GITHUB_REPOSITORY" --draft=false --latest=false
verify_tag "$immutable_tag"

# Recheck immediately before writing the rolling assets. Workflow concurrency
# owns serialization; the lease below additionally rejects an unexpected ref move.
[ "$(remote_tag "$channel")" = "$previous_tag" ] || fail "Channel changed during publication"
if [ "$evidence_name" = version.json ] && [ -n "$previous_tag" ] && [ "$previous_tag" != "$GITHUB_SHA" ]; then
  gh release download "$channel" --repo "$GITHUB_REPOSITORY" --pattern version.json --dir "$release_tmp" --clobber
  old_code=$(jq -er '.versionCode | select(type == "number" and . > 0 and . == floor)' "$release_tmp/version.json")
  new_code=$(jq -er .versionCode "$manifest")
  # A previous attempt may have uploaded both files and stopped before the tag
  # push. Only an exact copy of this commit's pinned manifest can resume at the
  # same code; a different source commit must increase the version code.
  if [ "$new_code" -le "$old_code" ] && ! cmp -s "$manifest" "$release_tmp/version.json"; then
    fail "Version code must increase ($new_code <= $old_code)"
  fi
fi

if gh release view "$channel" --repo "$GITHUB_REPOSITORY" >/dev/null; then
  gh release upload "$channel" "$artifact" "$manifest" --repo "$GITHUB_REPOSITORY" --clobber
else
  gh release create "$channel" "$artifact" "$manifest" --repo "$GITHUB_REPOSITORY" \
    --target "$GITHUB_SHA" --title "$channel" --prerelease="$prerelease" --latest=false
fi
verify_assets "$channel"
# A newly created release may already have created its tag.
current=$(remote_tag "$channel")
[ "$current" = "$previous_tag" ] || [ "$current" = "$GITHUB_SHA" ] || fail "Channel changed before tag update"
lease_ref=$(remote_tag "$channel" raw)
git push origin "${GITHUB_SHA}:refs/tags/$channel" "--force-with-lease=refs/tags/$channel:$lease_ref"
verify_tag "$channel"
latest=false
if [ "$channel" = stable ]; then latest=true; fi
gh release edit "$channel" --repo "$GITHUB_REPOSITORY" --prerelease="$prerelease" --latest="$latest"
