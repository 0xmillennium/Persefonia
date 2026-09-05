FROM eclipse-temurin:25.0.4_7-jre-ubi10-minimal@sha256:e582689824824ea54a961b9a6de9a48134bc8e34e25e118b416b000bbacc62da

ARG OCI_SOURCE=local://persefonia
ARG OCI_REVISION=LOCAL-WORKTREE
ARG OCI_VERSION=0.1.0

LABEL org.opencontainers.image.title="Persefonia" \
      org.opencontainers.image.source="${OCI_SOURCE}" \
      org.opencontainers.image.revision="${OCI_REVISION}" \
      org.opencontainers.image.version="${OCI_VERSION}"

WORKDIR /opt/persefonia

COPY --chown=10001:10001 --chmod=0444 app/build/libs/persefonia.jar /opt/persefonia/persefonia.jar

USER 10001:10001

CMD ["java", "-jar", "/opt/persefonia/persefonia.jar"]
