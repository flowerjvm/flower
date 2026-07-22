# Releasing Flower to Maven Central

Flower release artifacts use these coordinates:

```text
groupId:    io.github.flowerjvm
artifactId: flower-parent, flower-core, flower-eventloop, ...
version:    a non-SNAPSHOT release such as 0.1.1
```

The Java API packages use `io.github.flowerjvm.flower.*`.

## One-Time Publisher Setup

### 1. Verify the organization namespace

1. Sign in to the [Central Publisher Portal](https://central.sonatype.com/).
2. Open **View Namespaces** and add `io.github.flowerjvm`.
3. Copy the verification key assigned by the portal.
4. In the `flowerjvm` GitHub organization, create a temporary public repository
   whose name is exactly that verification key.
5. Return to the portal and select **Verify Namespace**. Delete the temporary
   repository after the namespace becomes **Verified**.

GitHub organization namespaces are not provisioned automatically from a
personal GitHub login. If the portal does not allow the organization
verification flow, contact Central Support and include the organization URL,
namespace, and verification key. See Sonatype's
[namespace registration guide](https://central.sonatype.org/register/namespace/).

### 2. Generate a Central Portal token

Generate a user token at
[central.sonatype.com/usertoken](https://central.sonatype.com/usertoken). Save
both generated values immediately; the portal does not show them again.

Add them as GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Generated token username |
| `CENTRAL_TOKEN_PASSWORD` | Generated token password |

### 3. Create and publish a GPG signing key

Maven Central requires the POM, primary JAR, source JAR, and Javadoc JAR to be
signed. Create a passphrase-protected key and publish its public half:

```bash
gpg --full-generate-key
gpg --list-keys --keyid-format long
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

Export the private key in ASCII-armored form:

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID
```

Add the complete output, including the `BEGIN` and `END` lines, and the key
passphrase as GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored private key |
| `MAVEN_GPG_PASSPHRASE` | Private-key passphrase |

Keep the private key and its revocation certificate backed up outside GitHub.
See Sonatype's [GPG guide](https://central.sonatype.org/publish/requirements/gpg/).

## Release Procedure

Maven Central releases are immutable. Check the version and artifacts before
publishing.

1. Set a non-SNAPSHOT version across the reactor:

   ```bash
   mvn -B versions:set \
     -DnewVersion=0.1.1 \
     -DprocessAllModules=true \
     -DgenerateBackupPoms=false
   ```

2. Build the same release profile used by CI:

   ```bash
   mvn -B -ntp -Prelease clean verify
   ```

3. Review and commit the version change, then create and push the matching
   `v`-prefixed tag:

   ```bash
   git commit -am "Release Flower 0.1.1"
   git tag v0.1.1
   git push origin main v0.1.1
   ```

4. Publish a GitHub Release for `v0.1.1`. The
   **Publish Release to Maven Central** workflow validates that the tag version
   and POM version match, builds all Maven reactor modules, creates source and
   Javadoc JARs, signs every artifact, and publishes through the Central
   Publisher Portal.

   The workflow can also be started manually. Select the commit containing the
   release POM version and enter that exact version in the workflow input.

5. Confirm the deployment is **Published** in the
   [Central Publisher Portal](https://central.sonatype.com/publishing/deployments).
   The workflow waits for this state before succeeding.

6. Move the main branch to the next development version:

   ```bash
   mvn -B versions:set \
     -DnewVersion=0.1.2-SNAPSHOT \
     -DprocessAllModules=true \
     -DgenerateBackupPoms=false
   git commit -am "Begin Flower 0.1.2-SNAPSHOT"
   git push origin main
   ```

## Local Bundle Dry Run

This exercises the Central release lifecycle without uploading anything. The
Maven settings used for the run must still contain a `central` server entry;
the plugin resolves that entry before honoring `central.skipPublishing`. With
a configured GPG key, omit `-Dgpg.skip=true` to verify the signatures too.

```bash
mvn -B -ntp -Prelease \
  -Dcentral.skipPublishing=true \
  -Dgpg.skip=true \
  clean deploy
```

The skip mode does not retain the final upload ZIP. Check each module's
`target` directory instead: the build must contain the main, source, and
Javadoc JARs for every non-POM artifact before a real release is attempted.

The separate `flower-check-gradle-plugin` build is not part of the Maven
reactor. It publishes two Maven Central coordinates through JReleaser:

```text
io.github.flowerjvm:flower-check-gradle-plugin:<version>
io.github.flowerjvm.flower-check:io.github.flowerjvm.flower-check.gradle.plugin:<version>
```

The second coordinate is the marker used by Gradle's `plugins {}` DSL. The
`Publish Gradle Plugin Release to Maven Central` workflow stages the plugin
JAR, sources, Javadocs, and marker POM, then signs and publishes the bundle.
For a release created before this workflow existed, run it manually with the
same non-SNAPSHOT version after the Maven reactor is visible on Central.
