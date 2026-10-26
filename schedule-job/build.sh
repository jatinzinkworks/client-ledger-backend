#!/usr/bin/bash
set -euo pipefail
# ----------------------------------------------------------------------------
# Build and push the client-ledger-daily-job image to Artifact Registry
# ----------------------------------------------------------------------------
# Exits immediately on any failure (set -e), undefined variables (set -u),
# or failed pipeline command (set -o pipefail).
#
# The job's version lives in VERSION beside this script, separate from the
# backend's version in pom.xml, and is tagged as client-ledger-daily-job-v<x.y>
# so it never collides with the backend's v<x.y> release tags.
# ----------------------------------------------------------------------------

# Run from this folder wherever the script is called from, so the relative
# paths below (and the Docker build context) are always schedule-job/.
cd "$(dirname "$0")"

# PROJECT, REGION and ARTIFACT_REPO are shared with the backend.
source ../variables.sh

JOB="client-ledger-daily-job"
JOB_REPO="${REGION}-docker.pkg.dev/${PROJECT}/${ARTIFACT_REPO}/${JOB}"
VERSION_FILE="VERSION"

# ---------- Step 0: Validate input ------------------------------------------
echo ""
echo "=========================="
echo "[0/8] Validating input"
echo "=========================="
BUMP_TYPE="${1:-}"
if [ "${BUMP_TYPE}" != "major" ] && [ "${BUMP_TYPE}" != "minor" ]; then
  echo "Usage: $0 <major|minor>" >&2
  echo "  major - bump major version (e.g., 1.2 -> 2.0)" >&2
  echo "  minor - bump minor version (e.g., 1.2 -> 1.3)" >&2
  exit 1
fi
echo "Bump type: ${BUMP_TYPE}"

# ---------- Step 1: Preflight git checks ------------------------------------
echo ""
echo "=========================="
echo "[1/8] Preflight git checks"
echo "=========================="
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "${CURRENT_BRANCH}" != "main" ]; then
  echo "Error: must be on 'main' branch (currently on '${CURRENT_BRANCH}')" >&2
  exit 1
fi
echo "Branch: main"
if [ -n "$(git status --porcelain)" ]; then
  echo "Error: working tree has uncommitted changes" >&2
  git status --short >&2
  exit 1
fi
echo "Working tree: clean"

# ---------- Step 2: Read current version ------------------------------------
echo ""
echo "=========================================="
echo "[2/8] Reading current version from ${VERSION_FILE}"
echo "=========================================="
if [ ! -f "${VERSION_FILE}" ]; then
  echo "ERROR: ${VERSION_FILE} not found" >&2
  exit 1
fi
CURRENT_VERSION=$(tr -d '[:space:]' < "${VERSION_FILE}")
if [ -z "${CURRENT_VERSION}" ]; then
  echo "ERROR: could not read version from ${VERSION_FILE}" >&2
  exit 1
fi
echo "Current version: ${CURRENT_VERSION}"

# ---------- Step 3: Compute new version -------------------------------------
echo ""
echo "=========================================="
echo "[3/8] Computing new version"
echo "=========================================="
if [ ! -x "../get_new_version.sh" ]; then
  echo "ERROR: ../get_new_version.sh not found or not executable" >&2
  exit 1
fi
NEW_VERSION=$(../get_new_version.sh "${CURRENT_VERSION}" "${BUMP_TYPE}")
if [ -z "${NEW_VERSION}" ]; then
  echo "ERROR: get_new_version.sh returned an empty version" >&2
  exit 1
fi
if [ "${NEW_VERSION}" = "${CURRENT_VERSION}" ]; then
  echo "ERROR: new version (${NEW_VERSION}) equals current version" >&2
  exit 1
fi
IMAGE="${JOB_REPO}:${NEW_VERSION}"
GIT_TAG="${JOB}-v${NEW_VERSION}"
echo "New version: ${NEW_VERSION}"
echo "Image:       ${IMAGE}"

# ---------- Step 4: Update VERSION ------------------------------------------
echo ""
echo "=========================================="
echo "[4/8] Updating ${VERSION_FILE} with new version"
echo "=========================================="
# If any step from here through push fails, revert VERSION so the working
# tree returns to a clean state matching origin/main.
trap 'echo ""; echo "Reverting ${VERSION_FILE} due to failure..."; git checkout -- "${VERSION_FILE}" || true' ERR

echo "${NEW_VERSION}" > "${VERSION_FILE}"
echo "${VERSION_FILE} updated: ${CURRENT_VERSION} -> ${NEW_VERSION}"

# ---------- Step 5: Build ---------------------------------------------------
echo ""
echo "=========================================="
echo "[5/8] Building Docker image"
echo "=========================================="
# APK_PATCH_BUST changes daily so the Dockerfile's apk security-patch layer is rebuilt against a
# fresh package index instead of being served from cache, which would keep already-fixed OS CVEs
# in the image even though --pull refreshed the base image.
docker build --pull --build-arg "APK_PATCH_BUST=$(date +%Y%m%d)" -t "${IMAGE}" .
echo "Build OK: ${IMAGE}"

# ---------- Step 6: Trivy security scan -------------------------------------
echo ""
echo "=========================================="
echo "[6/8] Scanning image with Trivy"
echo "=========================================="
echo "Failing on any vulnerabilities..."
docker run --rm \
  -v //var/run/docker.sock:/var/run/docker.sock \
  -v trivy-cache:/root/.cache/trivy \
  -v "/$(pwd)"://data -w //data \
  aquasec/trivy:latest --exit-code 1 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL image "${IMAGE}"
echo "Trivy scan OK: No Vulnerabilities"

# ---------- Step 7: Push ----------------------------------------------------
echo ""
echo "=========================================="
echo "[7/8] Pushing image to Artifact Registry"
echo "=========================================="
docker push "${IMAGE}"
echo "Push OK: ${IMAGE}"

# Build + push succeeded — disable the auto-revert trap before committing.
trap - ERR

# ---------- Step 8: Commit VERSION, push, and tag release -------------------
echo ""
echo "=========================================="
echo "[8/8] Committing ${VERSION_FILE} and tagging release"
echo "=========================================="
git add "${VERSION_FILE}"
git commit -m "Release ${JOB} ${NEW_VERSION}"
git push origin main
git tag -a "${GIT_TAG}" -m "Release ${JOB} ${NEW_VERSION}"
git push origin "${GIT_TAG}"
echo "Commit + tag OK: ${GIT_TAG}"

# ---------- Done ------------------------------------------------------------
echo ""
echo "=========================================="
echo "SUCCESS"
echo "=========================================="
echo "Job:       ${JOB}"
echo "Region:    ${REGION}"
echo "Image:     ${IMAGE}"
echo "Old → New: ${CURRENT_VERSION} → ${NEW_VERSION}"
echo "Tag:       ${GIT_TAG}"
echo ""
echo "To roll the job onto this image:"
echo "  gcloud run jobs update ${JOB} --image=${IMAGE} --region=${REGION} --project=${PROJECT}"
