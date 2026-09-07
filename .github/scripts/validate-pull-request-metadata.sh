#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${PR_NUMBER:?PR_NUMBER is required}"

if [[ ! "$PR_NUMBER" =~ ^[1-9][0-9]*$ ]]; then
	echo "pr_number must be a positive integer" >&2
	exit 1
fi

pull_request="$(gh api --header 'Accept: application/vnd.github+json' \
	"repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}")"
title="$(jq -r '.title // empty' <<<"$pull_request")"
labels="$(jq -r '[.labels[]?.name] | join(", ")' <<<"$pull_request")"
category_count="$(jq '[.labels[]?.name | select(. == "feature" or . == "change" or . == "bug" or . == "dependencies")] | length' <<<"$pull_request")"
has_skip_changelog="$(jq -r '[.labels[]?.name] | index("skip-changelog") != null' <<<"$pull_request")"
title_pattern='^[A-Za-z][A-Za-z0-9 /_-]*: .+$'
failures=()

if [[ ! "$title" =~ $title_pattern ]]; then
	failures+=("Title must use 'Area: Title' format")
fi
if [[ "$category_count" -gt 1 ]]; then
	failures+=("Use only one release category label: feature, change, bug, or dependencies")
elif [[ "$category_count" -eq 0 && "$has_skip_changelog" != "true" ]]; then
	failures+=("Add one release category label: feature, change, bug, dependencies, or skip-changelog")
fi

summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
{
	echo "### Pull request metadata"
	echo
	echo "- Title: ${title}"
	echo "- Labels: ${labels:-none}"
	if ((${#failures[@]} == 0)); then
		echo "- Result: valid"
	else
		echo "- Result: invalid"
		echo
		for failure in "${failures[@]}"; do
			echo "- ${failure}"
		done
	fi
} >> "$summary"

if ((${#failures[@]} > 0)); then
	printf 'Pull request metadata validation failed:\n' >&2
	printf -- '- %s\n' "${failures[@]}" >&2
	exit 1
fi
