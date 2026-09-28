#!/usr/bin/env bash

set -euo pipefail

API_BASE_URL=${API_BASE_URL:-http://localhost:8081}
PATCH_SUGGESTION_ID=${PATCH_SUGGESTION_ID:-${1:-20}}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command is missing: $1" >&2
    exit 1
  fi
}

assert_json() {
  local json=$1
  local filter=$2
  local message=$3

  if ! jq -e "$filter" >/dev/null <<<"$json"; then
    echo "Verification failed: $message" >&2
    echo "$json" | jq . >&2
    exit 1
  fi
}

require_command curl
require_command jq

echo "Verifying Phase 5 for patch suggestion $PATCH_SUGGESTION_ID"
echo "Backend: $API_BASE_URL"

echo "1/6 Checking backend health"
curl --silent --show-error --fail "$API_BASE_URL/health" >/dev/null

echo "2/6 Checking PR preflight"
preflight=$(curl --silent --show-error --fail \
  "$API_BASE_URL/patch-suggestions/$PATCH_SUGGESTION_ID/pull-request/preflight")
assert_json "$preflight" \
  ".patchSuggestionId == ($PATCH_SUGGESTION_ID | tonumber)" \
  "Preflight returned a different patch suggestion."
assert_json "$preflight" '.ready == true' \
  "Patch is not ready for pull request automation."

echo "3/6 Reading the stored PR record"
stored=$(curl --silent --show-error --fail \
  "$API_BASE_URL/patch-suggestions/$PATCH_SUGGESTION_ID/pull-request/status")
assert_json "$stored" '.refreshedFromGitHub == false' \
  "Stored status must identify PostgreSQL as its source."
assert_json "$stored" \
  '.status == "OPEN" or .status == "CLOSED" or .status == "MERGED" or .status == "UNKNOWN"' \
  "Stored PR status is outside the supported vocabulary."

echo "4/6 Refreshing the PR from GitHub"
refreshed=$(curl --silent --show-error --fail --request POST \
  "$API_BASE_URL/patch-suggestions/$PATCH_SUGGESTION_ID/pull-request/status/refresh")
assert_json "$refreshed" '.refreshedFromGitHub == true' \
  "Refresh response did not identify GitHub as its source."
assert_json "$refreshed" \
  ".pullRequestNumber == $(jq '.pullRequestNumber' <<<"$stored")" \
  "GitHub refresh returned a different pull request."

echo "5/6 Repeating PR creation to verify idempotency"
existing_pr=$(curl --silent --show-error --fail --request POST \
  "$API_BASE_URL/patch-suggestions/$PATCH_SUGGESTION_ID/pull-request")
assert_json "$existing_pr" '.created == false' \
  "Repeated PR creation unexpectedly created another pull request."
assert_json "$existing_pr" '.operationStatus == "ALREADY_EXISTS"' \
  "Repeated PR creation did not return ALREADY_EXISTS."

echo "6/6 Verifying missing-record behavior"
missing_id=2147483647
error_file=$(mktemp)
trap 'rm -f "$error_file"' EXIT
http_status=$(curl --silent --show-error \
  --output "$error_file" \
  --write-out '%{http_code}' \
  "$API_BASE_URL/patch-suggestions/$missing_id/pull-request/status")

if [[ "$http_status" != "404" ]]; then
  echo "Verification failed: missing PR record returned HTTP $http_status" >&2
  cat "$error_file" >&2
  exit 1
fi

echo
echo "Phase 5 verification passed."
echo "PR: $(jq -r '.pullRequestUrl' <<<"$refreshed")"
echo "Status: $(jq -r '.status' <<<"$refreshed")"
