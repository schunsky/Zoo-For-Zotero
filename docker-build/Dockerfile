# Build environment for Zoo for Zotero
# Matches the project's requirements: JDK 17, Gradle 8.13 (via wrapper), Android Gradle Plugin 8.11, compileSdk 36.
# Base is Ubuntu 24.04 "noble" - the same base as Linux Mint 22.x.
FROM eclipse-temurin:17-jdk-noble

# Your host user/group IDs, so files the build creates in your project folder belong to you, not root.
ARG UID=1000
ARG GID=1000
# Android command-line tools build number. If the download fails (404), copy the current number from
# https://developer.android.com/studio#command-line-tools-only ("commandlinetools-linux-<number>_latest.zip")
ARG CMDLINE_TOOLS_VERSION=13114758

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_OPTS="-Dorg.gradle.daemon=false"
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip git curl \
 && rm -rf /var/lib/apt/lists/*

# Android SDK command-line tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && curl -fsSL -o /tmp/tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip

# Accept licenses and pre-install what the project needs (baked into the image, so builds start faster)
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"

# Non-root build user with your host UID/GID. Ubuntu images ship a default "ubuntu" user (UID 1000); remove it.
# The SDK is owned by this user too, so Gradle can auto-install anything extra it asks for.
RUN (userdel -r ubuntu 2>/dev/null || true) \
 && groupadd -o -g "${GID}" builder \
 && useradd -o -m -u "${UID}" -g "${GID}" builder \
 && mkdir -p /home/builder/.gradle /home/builder/.android /project \
 && chown -R builder:builder /home/builder /project $ANDROID_HOME

USER builder
WORKDIR /project
CMD ["./gradlew", "tasks"]
