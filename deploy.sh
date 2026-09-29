#!/usr/bin/bash
set -euo pipefail
# ----------------------------------------------------------------------------
# Build, push, and deploy apex-data-governance-backend-ext to Cloud Run
# ----------------------------------------------------------------------------
# Exits immediately on any failure (set -e), undefined variables (set -u),
# or failed pipeline command (set -o pipefail).
# ----------------------------------------------------------------------------

source variables.sh

# ---------- Step 0: Validate input ------------------------------------------
echo ""
echo "=========================="
echo "[0/10] Validating input"
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
echo "[1/10] Preflight git checks"
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

# ---------- Step 2: Read current version from pom.xml -----------------------
echo ""
echo "=========================================="
echo "[2/10] Reading current version from pom.xml"
echo "=========================================="
CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
if [ -z "${CURRENT_VERSION}" ]; then
  echo "ERROR: could not read version from pom.xml" >&2
  exit 1
fi
echo "Current version: ${CURRENT_VERSION}"

# ---------- Step 3: Compute new version -------------------------------------
echo ""
echo "=========================================="
echo "[3/10] Computing new version"
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

# ---------- Step 4: Update pom.xml with new version -------------------------
echo ""
echo "=========================================="
echo "[4/10] Updating pom.xml with new version"
echo "=========================================="
# If any step from here through deploy fails, revert pom.xml so the working
# tree returns to a clean state matching origin/main.
trap 'echo ""; echo "Reverting pom.xml due to failure..."; git checkout -- pom.xml || true' ERR

mvn -B versions:set -DnewVersion="${NEW_VERSION}" -DgenerateBackupPoms=false
echo "pom.xml updated: ${CURRENT_VERSION} -> ${NEW_VERSION}"

# ---------- Step 5: Run unit tests and report coverage ----------------------
echo ""
echo "=========================================="
echo "[5/10] Running unit tests with coverage"
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

# ---------- Step 6: Build ---------------------------------------------------
echo ""
echo "=========================================="
echo "[6/10] Building Docker image"
echo "=========================================="
# APK_PATCH_BUST changes daily so the Dockerfile's apk security-patch layer is rebuilt against a
# fresh package index instead of being served from cache, which would keep already-fixed OS CVEs
# in the image even though --pull refreshed the base image.
docker build --pull --build-arg "APK_PATCH_BUST=$(date +%Y%m%d)" -t "${IMAGE}" .
echo "Build OK: ${IMAGE}"

# ---------- Step 7: Trivy security scan -------------------------------------
echo ""
echo "=========================================="
echo "[7/10] Scanning image with Trivy"
echo "=========================================="
echo "Failing on any vulnerabilities..."
docker run --rm \
  -v //var/run/docker.sock:/var/run/docker.sock \
  -v trivy-cache:/root/.cache/trivy \
  -v "/$(pwd)"://data -w //data \
  aquasec/trivy:latest --exit-code 1 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL image "${IMAGE}"
echo "Trivy scan OK: No Vulnerabilities"

# ---------- Step 8: Push ----------------------------------------------------
echo ""
echo "=========================================="
echo "[8/10] Pushing image to Artifact Registry"
echo "=========================================="
docker push "${IMAGE}"
echo "Push OK: ${IMAGE}"

# ---------- Step 9: Deploy --------------------------------------------------
echo ""
echo "=========================================="
echo "[9/10] Deploying to Cloud Run"
echo "=========================================="
gcloud run services update "${SERVICE}" \
  --region="${REGION}" \
  --project="${PROJECT}" \
  --image="${IMAGE}"
echo "Deploy OK: ${SERVICE}"

# Build + deploy succeeded — disable the auto-revert trap before committing.
trap - ERR

# ---------- Step 10: Commit pom.xml, push, and tag release ------------------
echo ""
echo "=========================================="
echo "[10/10] Committing pom.xml and tagging release"
echo "=========================================="
git add pom.xml
git commit -m "Release ${NEW_VERSION}"
git push origin main
git tag -a "v${NEW_VERSION}" -m "Release ${NEW_VERSION}"
git push origin "v${NEW_VERSION}"
echo "Commit + tag OK: v${NEW_VERSION}"

# ---------- Done ------------------------------------------------------------
echo ""
echo "=========================================="
echo "SUCCESS"
echo "=========================================="
echo "Service:   ${SERVICE}"
echo "Region:    ${REGION}"
echo "Image:     ${IMAGE}"
echo "Old → New: ${CURRENT_VERSION} → ${NEW_VERSION}"
echo "Tag:       v${NEW_VERSION}"
