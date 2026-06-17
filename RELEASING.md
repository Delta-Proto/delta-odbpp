# Release Process

This document describes how to release a new version to Maven Central.

The published artifact is the library module **`com.deltaproto:delta-odbpp`**.
The web application (`delta-odbpp-app`) is not published.

## Prerequisites

### 1. Maven Central Account

1. Create an account at [central.sonatype.com](https://central.sonatype.com/)
2. Verify ownership of the namespace `com.deltaproto`
3. Generate a user token:
   - Go to your account settings
   - Navigate to "Generate User Token"
   - Save the username and token securely

### 2. GPG Key

Create a GPG key for signing artifacts:

```bash
# Generate a new GPG key
gpg --full-generate-key

# List keys to get the key ID
gpg --list-secret-keys --keyid-format=long

# Export public key to a keyserver (required for Maven Central verification)
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### 3. Maven Settings

Add the following to your `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>YOUR_MAVEN_CENTRAL_USERNAME</username>
            <password>YOUR_MAVEN_CENTRAL_TOKEN</password>
        </server>
    </servers>
    <profiles>
        <profile>
            <id>release</id>
            <properties>
                <gpg.keyname>YOUR_GPG_KEY_ID</gpg.keyname>
            </properties>
        </profile>
    </profiles>
</settings>
```

## Creating a Release

### 1. Update Version

Ensure the version in the root `pom.xml` is set to the release version (without
`-SNAPSHOT`). The version is inherited by every module:

```xml
<version>1.0.0</version>
```

### 2. Build and Deploy

```bash
# Deploy to Maven Central
mvn clean deploy -Prelease

# If GPG prompts for a passphrase, or to pass it explicitly:
mvn clean deploy -Prelease -Dgpg.passphrase=YOUR_PASSPHRASE
```

The `release` profile attaches the sources and javadoc JARs, signs all
artifacts with GPG, and publishes via the Central Publishing plugin. The
`delta-odbpp-app` module is excluded from publishing.

### 3. Verify the Release

After deployment completes:

1. Check [Maven Central](https://central.sonatype.com/artifact/com.deltaproto/delta-odbpp) for the published artifact
2. It may take a few minutes to appear in search results

### 4. Tag the Release

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### 5. Prepare Next Development Version

```bash
# Update the root pom.xml to the next SNAPSHOT version
# Example: 1.0.1-SNAPSHOT
```

## Local Testing

To test the release build locally without publishing:

```bash
# Build with the release profile (skipping GPG)
mvn clean package -Prelease -Dgpg.skip=true

# Verify artifacts were created
ls odbpp-lib/target/*.jar
```

This should produce:
- `delta-odbpp-1.0.0.jar`
- `delta-odbpp-1.0.0-sources.jar`
- `delta-odbpp-1.0.0-javadoc.jar`

## Troubleshooting

### GPG Signing Issues

```bash
# Test GPG signing
echo "test" | gpg --clearsign

# List available keys
gpg --list-secret-keys --keyid-format=long
```

### Maven Central Publishing Issues

1. Verify your namespace is properly registered at central.sonatype.com
2. Ensure all required POM elements are present (name, description, url, license, developers, scm)
3. Check that source and javadoc JARs are being generated
4. Releases must not depend on `-SNAPSHOT` artifacts
