#!/bin/sh
# Posts every company's due services to its ledger by calling the backend's bulk posting endpoint.
# Run as a Cloud Run Job; exits non-zero when the call fails or any company fails to post, so the
# execution is marked failed and can be alerted on.
#
# Environment:
#   SERVICE_URL       Base URL of the backend Cloud Run service, e.g. https://backend-xxxx.a.run.app
#   REQUEST_TIMEOUT   Seconds to wait for the backend's reply (default 1800); keep it within the
#                     backend service's request timeout and the job's task timeout.
set -eu

: "${SERVICE_URL:?SERVICE_URL must be set to the backend's base URL}"
SERVICE_URL="${SERVICE_URL%/}"
REQUEST_TIMEOUT="${REQUEST_TIMEOUT:-1800}"
ENDPOINT="${SERVICE_URL}/psc/cl/v1/ledger-entries/post-due"

# An ID token for the job's service account, accepted by a Cloud Run service that requires
# authentication. The token itself is never printed.
TOKEN=$(curl -sSf -H "Metadata-Flavor: Google" \
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=${SERVICE_URL}")

echo "Posting due services via ${ENDPOINT}"
BODY=$(curl -sS --fail-with-body --max-time "${REQUEST_TIMEOUT}" -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json" \
    "${ENDPOINT}") || {
    echo "Bulk posting request failed: ${BODY:-no response}" >&2
    exit 1
}

echo "${BODY}"

# The endpoint answers 200 even when some companies failed; those are listed under "failures".
if ! echo "${BODY}" | grep -q '"failures":\[\]'; then
    echo "One or more companies failed to post; see failures above" >&2
    exit 1
fi

echo "Bulk posting completed"
