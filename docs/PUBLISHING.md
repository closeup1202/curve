# Publishing Guide

This guide explains how to publish Curve to Maven Central.

> **Note**: As of January 2024, Sonatype has migrated from the old JIRA-based system (issues.sonatype.org) to the new **Central Publisher Portal**.

## Prerequisites

Before publishing, you need:

1. **Sonatype Central Portal Account**
2. **Namespace (Group ID) Verification**
3. **GPG Key** for signing artifacts
4. **GitHub Secrets** configured

---

## Step 1: Create Sonatype Central Portal Account

### 1.1 Sign Up

1. Go to https://central.sonatype.com
2. Click **"Sign In"** (top right)
3. Choose sign-up method:
   - **GitHub** (recommended - easiest for `io.github.*` namespace)
   - Google
   - Username/Password

### 1.2 Verify Email

Check your email and verify your account.

---

## Step 2: Register Namespace (Group ID)

### 2.1 Go to Namespace Registration

1. Login to https://central.sonatype.com
2. Click on your profile → **"View Namespaces"**
3. Click **"Add Namespace"**

### 2.2 Register `io.github.closeup1202`

1. Enter namespace: `io.github.closeup1202`
2. Select verification method: **GitHub**
3. Follow the verification steps:
   - Create a temporary **public repository** with the specified name
   - Sonatype will verify your GitHub account ownership
   - After verification, you can delete the temporary repository

### 2.3 Wait for Verification

- GitHub-based verification is usually automatic (within minutes)
- You'll see the namespace status change to **"Verified"**

---

## Step 3: Generate User Token

### 3.1 Create Token for Publishing

1. Login to https://central.sonatype.com
2. Click on your profile → **"View Account"**
3. Click **"Generate User Token"**
4. Save the generated credentials:
   - **Username**: (token username)
   - **Password**: (token password)

> **Important**: Save these credentials securely. You won't be able to see the password again.

---

## Step 4: Generate GPG Key

### 4.1 Install GPG

```bash
# macOS
brew install gnupg

# Ubuntu/Debian
sudo apt-get install gnupg

# Windows (PowerShell)
winget install GnuPG.GnuPG
# Or download from https://gpg4win.org/
```

### 4.2 Generate Key Pair

```bash
gpg --full-generate-key
```

Select:
- Key type: `RSA and RSA`
- Key size: `4096`
- Expiration: `0` (does not expire) or your preference
- Real name: `closeup1202`
- Email: `closeup1202@gmail.com`
- Passphrase: (remember this!)

### 4.3 Get Key ID

```bash
gpg --list-secret-keys --keyid-format LONG

# Output example:
# sec   rsa4096/ABCDEF1234567890 2024-01-01 [SC]
#       1234567890ABCDEF1234567890ABCDEF12345678
# uid                 [ultimate] closeup1202 <closeup1202@gmail.com>

# Key ID: ABCDEF1234567890 (16 characters after rsa4096/)
# Or short form: last 8 characters
```

### 4.4 Upload Public Key to Keyserver

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Also upload to other keyservers for redundancy
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 4.5 Export Private Key for GitHub Actions

```bash
# Export private key
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc

# View content (copy this for GitHub Secret)
cat private-key.asc
```

---

## Step 5: Configure GitHub Secrets

Go to your repository → **Settings** → **Secrets and variables** → **Actions**

Add these secrets:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `OSSRH_USERNAME` | Token username from Step 3 | Central Portal token username |
| `OSSRH_PASSWORD` | Token password from Step 3 | Central Portal token password |
| `GPG_KEY_ID` | `ABCDEF1234567890` | Your GPG key ID |
| `GPG_PRIVATE_KEY` | Content of `private-key.asc` | Entire file including headers |
| `GPG_PASSPHRASE` | Your GPG passphrase | The password you set for GPG key |

---

## Step 6: Test Locally (Optional)

### 6.1 Configure Local Credentials

Create or edit `~/.gradle/gradle.properties`:

```properties
ossrhUsername=your-token-username
ossrhPassword=your-token-password
signing.keyId=ABCDEF1234567890
signing.password=your-gpg-passphrase
signing.secretKeyRingFile=C:/Users/YourName/.gnupg/secring.gpg
```

### 6.2 Publish SNAPSHOT

```bash
./gradlew publish
```

Check your artifacts at: https://central.sonatype.com (Deployments tab)

---

## Step 7: Release

### 7.1 Update Version

Edit `gradle.properties`:
```properties
# Change from SNAPSHOT to release version
version=0.0.1
```

### 7.2 Commit and Tag

```bash
git add .
git commit -m "Release v0.0.1"
git tag v0.0.1
git push origin main --tags
```

### 7.3 Automatic Release

The GitHub Actions workflow will automatically:
1. Build and test
2. Sign artifacts with GPG
3. Publish to Central Portal
4. Create GitHub Release

### 7.4 Manual Release via Portal (if needed)

1. Go to https://central.sonatype.com
2. Click **"Deployments"** tab
3. Find your deployment
4. Click **"Publish"** to release to Maven Central

---

## Step 8: Verify Publication

After release, your artifacts will be available at:

- **Maven Central Search**: https://search.maven.org/search?q=g:io.github.closeup1202
- **Direct URL**: https://repo1.maven.org/maven2/io/github/closeup1202/

> **Note**: It may take 10-30 minutes for artifacts to sync to Maven Central after publishing.

---

## Usage After Publication

Users can add your library:

**Gradle (Kotlin DSL)**:
```kotlin
dependencies {
    implementation("io.github.closeup1202:curve-spring-boot-autoconfigure:0.0.1")
}
```

**Gradle (Groovy)**:
```groovy
dependencies {
    implementation 'io.github.closeup1202:curve-spring-boot-autoconfigure:0.0.1'
}
```

**Maven**:
```xml
<dependency>
    <groupId>io.github.closeup1202</groupId>
    <artifactId>curve-spring-boot-autoconfigure</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## Troubleshooting

### GPG Key Not Found on Keyserver

```bash
# Re-upload to multiple keyservers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID
```

### Namespace Verification Failed

- Ensure the temporary repository is **public**
- Repository name must match exactly what Sonatype specifies
- Try verification again after a few minutes

### Publication Failed

Check the **Deployments** tab in Central Portal for specific errors:
- Missing POM information
- Invalid GPG signatures
- Missing Javadoc/Sources JARs

### "401 Unauthorized" Error

- Regenerate your User Token in Central Portal
- Update GitHub Secrets with new credentials

---

## Quick Reference

| Resource | URL |
|----------|-----|
| Central Portal | https://central.sonatype.com |
| Maven Central Search | https://search.maven.org |
| GPG Keyserver | https://keyserver.ubuntu.com |
| Support Email | central-support@sonatype.com |

---

## Summary Checklist

- [ ] Create Central Portal account (https://central.sonatype.com)
- [ ] Register and verify namespace `io.github.closeup1202`
- [ ] Generate User Token
- [ ] Generate GPG key and upload to keyserver
- [ ] Configure GitHub Secrets
- [ ] Update version in `gradle.properties`
- [ ] Create git tag and push
- [ ] Verify publication on Maven Central
