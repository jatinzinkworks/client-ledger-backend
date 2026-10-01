#!/usr/bin/bash
PROJECT="test-project"
REGION="asia-south2"
SERVICE="client-ledger-backend"
ARTIFACT_REPO="docker-release"
REPO=${REGION}"-docker.pkg.dev/${PROJECT}/${ARTIFACT_REPO}/${SERVICE}"
VERSION="test"
IMAGE="${REPO}:${VERSION}"
