# Cloud Run Job image that triggers the backend's bulk posting of due services.
# Built and pushed by build.sh in this folder.
#
# Alpine with curl from its package repository rather than curlimages/curl, so the apk upgrade
# below patches curl along with the rest of the OS and the Trivy scan in build.sh can pass.
FROM alpine:3.22

# APK_PATCH_BUST busts the Docker build cache for this layer so it always re-runs against a fresh
# package index instead of reusing a stale cached layer; build.sh passes the current date.
ARG APK_PATCH_BUST=0
RUN echo "apk security-patch layer, bust=${APK_PATCH_BUST}" && \
    apk update && \
    apk upgrade --no-cache && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --chmod=755 post-due.sh /usr/local/bin/post-due.sh

USER appuser

ENTRYPOINT ["/usr/local/bin/post-due.sh"]
