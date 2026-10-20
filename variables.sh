#!/usr/bin/bash
PROJECT="project-753cc906-dd6c-404e-b65"
REGION="asia-south2"
SERVICE="client-ledger-backend"
ARTIFACT_REPO="docker-release-artifactory"
REPO=${REGION}"-docker.pkg.dev/${PROJECT}/${ARTIFACT_REPO}/${SERVICE}"
VERSION="test"
IMAGE="${REPO}:${VERSION}"
