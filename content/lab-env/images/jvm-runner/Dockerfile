# Lab runner: JDK 21, the Kotlin compiler, and pinned library sets for labs whose code needs JDBC, Kafka, or Spring.
# Libraries are resolved once while the image is built, so lab builds and runs never download anything.
# Bump the tag in ../../jvm-runner.sh and docker.main.kts whenever this file or libs/ changes.
ARG MAVEN_IMAGE=maven@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320
ARG JDK_IMAGE=eclipse-temurin@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a

FROM ${MAVEN_IMAGE} AS libs
COPY libs /libs-src
RUN for set in /libs-src/*/; do \
      name=$(basename "$set"); \
      mvn -q -B -f "$set/pom.xml" dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="/opt/lab-libs/$name" || exit 1; \
    done

FROM ${JDK_IMAGE}
ARG KOTLIN_VERSION=2.4.21
ARG KOTLIN_SHA256=7cc140e76daf416a0424a5557f99afd7ca89ebd463ea1101261e3e01c33e75cd
RUN apt-get update && apt-get install -y --no-install-recommends unzip curl && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /tmp/kotlin.zip "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
 && echo "${KOTLIN_SHA256}  /tmp/kotlin.zip" | sha256sum -c - \
 && unzip -q /tmp/kotlin.zip -d /opt && rm /tmp/kotlin.zip
ENV PATH=/opt/kotlinc/bin:$PATH
COPY --from=libs /opt/lab-libs /opt/lab-libs
COPY lab-build lab-run /usr/local/bin/
