#!/usr/bin/bash
set -euo pipefail
PROJECT="nexa-477913"
REGION="europe-west2"
SERVICE="apex-data-governance-backend-ext"

ACTIVE_REVISION=$(gcloud run services describe "${SERVICE}" \
  --region="${REGION}" \
  --project="${PROJECT}" \
  --format='value(status.latestReadyRevisionName)' \
  | tr -d '\r')

REVISIONS_TO_DELETE=$(gcloud run revisions list \
  --service="${SERVICE}" \
  --region="${REGION}" \
  --project="${PROJECT}" \
  --format='value(metadata.name)' \
  --filter="metadata.name!=${ACTIVE_REVISION}" \
  | tr -d '\r' \
  | tail -n +4)

if [ -z "${REVISIONS_TO_DELETE}" ]; then
  echo "No old revisions to delete."
  exit 0
fi

echo "Active revision (kept): ${ACTIVE_REVISION}"
echo "Revisions to delete:"
echo "${REVISIONS_TO_DELETE}"
echo ""

while read -r REV; do
  [ -z "$REV" ] && continue
  echo "Deleting revision: $REV"
  gcloud run revisions delete "$REV" --region="${REGION}" --project="${PROJECT}" --quiet --no-async
done <<< "${REVISIONS_TO_DELETE}"
