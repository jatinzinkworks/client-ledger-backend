#!/usr/bin/bash
set -euo pipefail

source variables.sh

BUMP_TYPE="dev"
echo "Bump type: ${BUMP_TYPE}"

# ---------- Step 1: Read current version from pom.xml -----------------------
echo ""
echo "=========================================="
echo "[1] Reading current version from pom.xml"
echo "=========================================="
CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
if [ -z "${CURRENT_VERSION}" ]; then
  echo "ERROR: could not read version from pom.xml" >&2
  exit 1
fi
echo "Current version: ${CURRENT_VERSION}"

# ---------- Step 2: Compute new version -------------------------------------
echo ""
echo "=========================================="
echo "[2] Computing new version"
echo "=========================================="
if [ ! -x "./get_new_version.sh" ]; then
  echo "ERROR: ./get_new_version.sh not found or not executable" >&2
  exit 1
fi
NEW_VERSION=$(./get_new_version.sh "${CURRENT_VERSION}" "${BUMP_TYPE}")
if [ -z "${NEW_VERSION}" ]; then
  echo "ERROR: get_new_version.sh returned an empty version" >&2
  exit 1
fi
if [ "${NEW_VERSION}" = "${CURRENT_VERSION}" ]; then
  echo "ERROR: new version (${NEW_VERSION}) equals current version" >&2
  exit 1
fi
IMAGE="${REPO}:${NEW_VERSION}"
echo "New version: ${NEW_VERSION}"
echo "Image:       ${IMAGE}"

# ---------- Step 3: Run unit tests and report coverage ----------------------
echo ""
echo "=========================================="
echo "[3] Running unit tests with coverage"
echo "=========================================="
mvn -B clean test jacoco:report
REPORT="target/site/jacoco/jacoco.csv"
if [ ! -f "${REPORT}" ]; then
  echo "ERROR: JaCoCo report not found at ${REPORT}" >&2
  echo "       Ensure the jacoco-maven-plugin is configured in pom.xml" >&2
  exit 1
fi
echo ""
echo "Coverage summary:"
awk -F, 'NR>1 {
  im+=$4; ic+=$5;
  bm+=$6; bc+=$7;
  lm+=$8; lc+=$9;
  mm+=$12; mc+=$13;
}
END {
  ti = ic+im; tb = bc+bm; tl = lc+lm; tm = mc+mm;
  pi = (ti>0) ? (ic/ti)*100 : 0;
  pb = (tb>0) ? (bc/tb)*100 : 0;
  pl = (tl>0) ? (lc/tl)*100 : 0;
  pm = (tm>0) ? (mc/tm)*100 : 0;
  printf "  Instructions: %6.2f%% (%d/%d)\n", pi, ic, ti;
  printf "  Branches:     %6.2f%% (%d/%d)\n", pb, bc, tb;
  printf "  Lines:        %6.2f%% (%d/%d)\n", pl, lc, tl;
  printf "  Methods:      %6.2f%% (%d/%d)\n", pm, mc, tm;
}' "${REPORT}"
echo ""
echo "HTML report: target/site/jacoco/index.html"

# ---------- Step 4: Build ---------------------------------------------------
echo ""
echo "=========================================="
echo "[4] Building Docker image"
echo "=========================================="
# APK_PATCH_BUST changes daily so the Dockerfile's apk security-patch layer is rebuilt against a
# fresh package index instead of being served from cache, which would keep already-fixed OS CVEs
# in the image even though --pull refreshed the base image.
docker build --pull --build-arg "APK_PATCH_BUST=$(date +%Y%m%d)" -t "${IMAGE}" .
echo "Build OK: ${IMAGE}"

# ---------- Step 5: Push ----------------------------------------------------
echo ""
echo "=========================================="
echo "[5] Pushing image to Artifact Registry"
echo "=========================================="
docker push "${IMAGE}"
echo "Push OK: ${IMAGE}"

# ---------- Step 6: Deploy --------------------------------------------------
echo ""
echo "=========================================="
echo "[6] Deploying to Cloud Run"
echo "=========================================="
gcloud run services update "${SERVICE}" \
  --region="${REGION}" \
  --project="${PROJECT}" \
  --image="${IMAGE}"
echo "Deploy OK: ${SERVICE}"

# Build + deploy succeeded — disable the auto-revert trap before committing.
trap - ERR

# ---------- Done ------------------------------------------------------------
echo ""
echo "=========================================="
echo "SUCCESS"
echo "=========================================="
echo "Service:   ${SERVICE}"
echo "Region:    ${REGION}"
echo "Image:     ${IMAGE}"
echo "Version:  ${NEW_VERSION}"