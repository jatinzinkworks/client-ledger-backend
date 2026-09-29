#!/usr/bin/bash
PROJECT="zinkworks-tools-poc"
REGION="europe-west2"
SERVICE="zinkworks-tripfold-backend"
ARTIFACT_REPO="zinkworks-tools-docker-release"
REPO=${REGION}"-docker.pkg.dev/${PROJECT}/${ARTIFACT_REPO}/${SERVICE}"
VERSION="test"
IMAGE="${REPO}:${VERSION}"
