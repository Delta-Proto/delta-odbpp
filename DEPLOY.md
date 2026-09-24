# Deploy to Maven Central

Quick reference for deploying a release. See [RELEASING.md](RELEASING.md) for prerequisites and setup.

Only the library module `com.deltaproto:delta-odbpp` is published; the web
application is skipped automatically by the `release` profile.

## Version convention

`main` always carries a `-SNAPSHOT` version. A release is a pair of commits:
one that sets the release version and is tagged, immediately followed by one
that moves `main` to the next `-SNAPSHOT`. A checkout of `main` therefore never
builds something that claims to be a published version.

The version lives in **three** files, which must agree: the root `pom.xml`
and the `<parent>` block of `odbpp-lib/pom.xml` and `odbpp-app/pom.xml`.

## Steps

### 1. Set the release version

Change `X.Y.Z-SNAPSHOT` to `X.Y.Z` in all three poms and commit:

```bash
sed -i 's#<version>X.Y.Z-SNAPSHOT</version>#<version>X.Y.Z</version>#' pom.xml odbpp-lib/pom.xml odbpp-app/pom.xml
git commit -am "Release X.Y.Z"
```

### 2. Build and deploy

```bash
MAVEN_GPG_PASSPHRASE="$(cat .mvn-gpg-passphrase)" mvn clean deploy -Prelease
```

The GPG passphrase is stored in `.mvn-gpg-passphrase` (gitignored, mode 600).
Passing it through the environment keeps it out of the process list; the
older `-Dgpg.passphrase=...` form still works but the plugin warns about it.
If signing fails with "Bad passphrase", the file is stale; test it with
`echo test | gpg --batch --pinentry-mode loopback --passphrase-file .mvn-gpg-passphrase --clearsign`.

The build runs the full test suite, signs every artifact, uploads the bundle
and waits until Central reports it published.

### 3. Verify the release

Check [Maven Central](https://central.sonatype.com/artifact/com.deltaproto/delta-odbpp)
for the published artifact, or:

```bash
curl -s https://repo1.maven.org/maven2/com/deltaproto/delta-odbpp/maven-metadata.xml | grep '<release>'
```

### 4. Tag and push

```bash
git tag -a vX.Y.Z -m "Release version X.Y.Z"
git push origin main vX.Y.Z
```

### 5. Create the GitHub release

```bash
gh release create vX.Y.Z --title "Release X.Y.Z" --notes "# Changes

- Description of changes"
```

### 6. Move `main` to the next snapshot

This step is part of the release, not optional. Do it right after tagging:

```bash
sed -i 's#<version>X.Y.Z</version>#<version>X.Y.Z+1-SNAPSHOT</version>#' pom.xml odbpp-lib/pom.xml odbpp-app/pom.xml
git commit -am "Start X.Y.Z+1-SNAPSHOT"
git push origin main
```

Anything that runs the web application from source against the local Maven
repository (for example `mvn spring-boot:run` in `odbpp-app/`) now needs the
snapshot library and parent pom installed: `mvn install -DskipTests -pl odbpp-lib -am`.
