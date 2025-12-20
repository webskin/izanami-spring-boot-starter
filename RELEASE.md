Release secrets and Maven Central setup
======================================

This repository's release workflow publishes signed artifacts to Maven Central
and creates a GitHub Release. The workflow expects the following GitHub Actions
secrets to be configured.

Required GitHub Actions secrets
-------------------------------

- `GPG_PRIVATE_KEY`: ASCII-armored private key used to sign artifacts.
  Example export:
  `gpg --export-secret-keys --armor <KEY_ID>`
- `GPG_PASSPHRASE`: passphrase for the private key (leave empty only if the key
  is not protected, which is not recommended).
- `CENTRAL_USERNAME`: Sonatype Central publishing username (token username).
- `CENTRAL_TOKEN`: Sonatype Central publishing token (token password).

Where to configure secrets
--------------------------

1. GitHub:
   - Repository Settings -> Secrets and variables -> Actions
   - Add each secret listed above.

2. Maven Central (Sonatype Central Portal):
   - Create or select your namespace and project in the Central Portal.
   - Generate a publishing token.
   - Use the generated token username/password for `CENTRAL_USERNAME` and
     `CENTRAL_TOKEN`.

How the workflow uses these
---------------------------

- The GitHub Actions workflow sets up Maven with:
  - `server-id: central`
  - `server-username: CENTRAL_USERNAME`
  - `server-password: CENTRAL_TOKEN`
  - `gpg-private-key: GPG_PRIVATE_KEY`
  - `gpg-passphrase: GPG_PASSPHRASE`
- Maven then signs and deploys artifacts with these credentials.

Notes
-----

- The workflow is triggered by tags like `v1.2.3` or `v1.2.3-rc1`.
- Make sure your GPG key is trusted and not expired.

Generate a GPG key on Ubuntu
----------------------------

1. Install GnuPG (if needed):
   `sudo apt-get update && sudo apt-get install -y gnupg`

2. Generate a new key:
   `gpg --full-generate-key`
   - Type: `ECC (sign and encrypt)`
   - Curve: `ed25519`
   - Set a strong passphrase
   - Use the email you will publish with

3. List the key id:
   `gpg --list-secret-keys --keyid-format=long`
   Example: `sec   ed25519/ABCDEF1234567890`

4. Export the private key for GitHub:
   `gpg --export-secret-keys --armor ABCDEF1234567890`
   Copy the full block into the `GPG_PRIVATE_KEY` secret.

5. Add the passphrase to `GPG_PASSPHRASE` in GitHub secrets.

Optional: publish the public key:
`gpg --keyserver hkps://keys.openpgp.org --send-keys ABCDEF1234567890`
