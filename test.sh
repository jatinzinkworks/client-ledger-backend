#!/usr/bin/bash
set -euo pipefail

source variables.sh
echo "Version: ${VERSION}"
echo "Image:   ${IMAGE}"

# ---------- Step 1: Run unit tests and report coverage ----------------------
echo ""
echo "=========================================="
echo "[1/3] Running unit tests with coverage"
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

# ---------- Step 2: Build ---------------------------------------------------
echo ""
echo "=========================================="
echo "[2/3] Building Docker image"
echo "=========================================="
# APK_PATCH_BUST changes daily so the Dockerfile's apk security-patch layer is rebuilt against a
# fresh package index instead of being served from cache, which would keep already-fixed OS CVEs
# in the image even though --pull refreshed the base image.
docker build --pull --build-arg "APK_PATCH_BUST=$(date +%Y%m%d)" -t "${IMAGE}" .
echo "Build OK: ${IMAGE}"

# ---------- Step 3: Trivy security scan -------------------------------------
echo ""
echo "=========================================="
echo "[3/3] Scanning image with Trivy"
echo "=========================================="
echo "Failing on any vulnerabilities..."
docker run --rm \
  -v //var/run/docker.sock:/var/run/docker.sock \
  -v "/$(pwd)"://data -w //data \
  aquasec/trivy:latest --exit-code 1 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL image "${IMAGE}"
echo "Trivy scan OK: No Vulnerabilities"
