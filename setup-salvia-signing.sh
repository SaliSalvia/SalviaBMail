#!/data/data/com.termux/files/usr/bin/bash
# SalviaBMail production signing setup for GitHub Actions.
# Run explicitly with: bash setup-salvia-signing.sh
# Uses the exact secret names consumed by .github/workflows/build-apk.yml.

set -euo pipefail
umask 077

DEFAULT_KS="SalviaBMail.jks"
DEFAULT_ALIAS="salviabmail"
DEFAULT_VALIDITY=10000
DEFAULT_REPO="SaliSalvia/SalviaBMail"

SEC_KEYSTORE="SALI_KEYSTORE_BASE64"
SEC_STOREPASS="SALI_KEYSTORE_PASSWORD"
SEC_ALIAS="SALI_KEY_ALIAS"
SEC_KEYPASS="SALI_KEY_PASSWORD"

say()  { printf ':: %s\n' "$*"; }
ok()   { printf '[OK] %s\n' "$*"; }
warn() { printf '[!] %s\n' "$*" >&2; }
die()  { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

command -v gh >/dev/null 2>&1 || die "gh missing; install GitHub CLI."
command -v git >/dev/null 2>&1 || die "git missing."
command -v keytool >/dev/null 2>&1 || die "keytool missing; install OpenJDK 17."
command -v base64 >/dev/null 2>&1 || die "base64 missing."
command -v tr >/dev/null 2>&1 || die "tr missing."
gh auth status >/dev/null 2>&1 || die "GitHub CLI is not authenticated. Run: gh auth login."

GH_USER="$(gh api user -q .login)" || die "Could not determine authenticated GitHub user."
REPO="$DEFAULT_REPO"
REMOTE="$(git remote get-url origin 2>/dev/null || true)"
case "$REMOTE" in
  *github.com*)
    REMOTE="${REMOTE#*github.com[:/]}"
    REMOTE="${REMOTE%.git}"
    REMOTE="${REMOTE%/}"
    if [[ "$REMOTE" == */* ]]; then REPO="$REMOTE"; fi
    ;;
esac

printf '\nTarget repository: %s\n' "$REPO"
read -r -p "Repository owner/name [$REPO]: " ANSWER
REPO="${ANSWER:-$REPO}"
gh repo view "$REPO" --json nameWithOwner -q .nameWithOwner >/dev/null 2>&1 \
  || die "Repository '$REPO' not found or inaccessible."
ok "Authenticated as $GH_USER; target is $REPO."

printf '\nKeystore settings (press Enter to accept defaults).\n'
KS_FILE="$DEFAULT_KS"
read -r -p "Keystore file [$KS_FILE]: " ANSWER
KS_FILE="${ANSWER:-$KS_FILE}"

if [[ -e "$KS_FILE" ]]; then
  warn "File '$KS_FILE' already exists."
  read -r -p "Replace it with a NEW signing key? Type YES to continue: " OVERWRITE
  [[ "$OVERWRITE" == "YES" ]] || die "Existing keystore preserved; no secrets changed."
  rm -f -- "$KS_FILE"
fi

while true; do
  read -r -s -p "Keystore password (minimum 8 characters): " STORE_PASS
  printf '\n'
  (( ${#STORE_PASS} >= 8 )) || { warn "Password is too short."; continue; }
  read -r -s -p "Confirm keystore password: " CONFIRM
  printf '\n'
  [[ "$STORE_PASS" == "$CONFIRM" ]] && break
  warn "Passwords do not match."
done

read -r -p "Use the same password for the key? [Y/n]: " SAME
SAME="${SAME:-Y}"
if [[ "$SAME" =~ ^[Yy]$ ]]; then
  KEY_PASS="$STORE_PASS"
else
  while true; do
    read -r -s -p "Key password (minimum 8 characters): " KEY_PASS
    printf '\n'
    (( ${#KEY_PASS} >= 8 )) || { warn "Password is too short."; continue; }
    read -r -s -p "Confirm key password: " CONFIRM
    printf '\n'
    [[ "$KEY_PASS" == "$CONFIRM" ]] && break
    warn "Passwords do not match."
  done
fi

read -r -p "Key alias [$DEFAULT_ALIAS]: " ALIAS
ALIAS="${ALIAS:-$DEFAULT_ALIAS}"
read -r -p "Validity in days [$DEFAULT_VALIDITY]: " VALIDITY
VALIDITY="${VALIDITY:-$DEFAULT_VALIDITY}"
[[ "$VALIDITY" =~ ^[0-9]+$ ]] && (( VALIDITY >= 9000 )) || die "Validity must be numeric and at least 9000 days."

printf '\nCertificate identity. CN is required; other values may be blank.\n'
CN=""
while [[ -z "$CN" ]]; do
  read -r -p "Common Name / developer name: " CN
done
read -r -p "Organization [SaliSalvia]: " ORG
ORG="${ORG:-SaliSalvia}"
read -r -p "Organizational unit [Android]: " OU
OU="${OU:-Android}"
read -r -p "Country code (2 letters, optional): " COUNTRY
if [[ -n "$COUNTRY" && ! "$COUNTRY" =~ ^[A-Za-z]{2}$ ]]; then
  die "Country code must contain exactly two letters."
fi
DNAME="CN=$CN, OU=$OU, O=$ORG"
[[ -n "$COUNTRY" ]] && DNAME="$DNAME, C=$COUNTRY"

STORE_TYPE="PKCS12"
if [[ "$KEY_PASS" != "$STORE_PASS" ]]; then
  STORE_TYPE="JKS"
  warn "Different store and key passwords selected; using JKS."
fi

say "Generating RSA-2048 release key in $KS_FILE."
KS_STORE_PASS="$STORE_PASS" KS_KEY_PASS="$KEY_PASS" \
  keytool -genkeypair -v -keystore "$KS_FILE" -storetype "$STORE_TYPE" \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity "$VALIDITY" \
    -storepass:env KS_STORE_PASS -keypass:env KS_KEY_PASS -dname "$DNAME" \
  || die "keytool failed; no GitHub secrets were changed."

KS_STORE_PASS="$STORE_PASS" keytool -list -keystore "$KS_FILE" \
  -storetype "$STORE_TYPE" -storepass:env KS_STORE_PASS >/dev/null \
  || die "Keystore verification failed."
ok "Keystore created and verified."

printf '\nCertificate SHA-256 fingerprint:\n'
KS_STORE_PASS="$STORE_PASS" keytool -list -v -keystore "$KS_FILE" \
  -storetype "$STORE_TYPE" -storepass:env KS_STORE_PASS 2>/dev/null \
  | grep -E 'SHA256:' || true

# The repository ignores *.jks and *.keystore. Never commit the private key.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if ! git check-ignore -q "$KS_FILE"; then
    warn "The keystore is not ignored by Git; adding it to .gitignore."
    printf '\n%s\n' "$KS_FILE" >> .gitignore
  fi
fi

printf '\nUploading signing secrets to %s ...\n' "$REPO"
base64 "$KS_FILE" | tr -d '\r\n' | gh secret set "$SEC_KEYSTORE" -R "$REPO" \
  || die "Could not upload $SEC_KEYSTORE."
printf '%s' "$STORE_PASS" | gh secret set "$SEC_STOREPASS" -R "$REPO" \
  || die "Could not upload $SEC_STOREPASS."
printf '%s' "$ALIAS" | gh secret set "$SEC_ALIAS" -R "$REPO" \
  || die "Could not upload $SEC_ALIAS."
printf '%s' "$KEY_PASS" | gh secret set "$SEC_KEYPASS" -R "$REPO" \
  || die "Could not upload $SEC_KEYPASS."

ok "All four workflow-compatible secrets uploaded."
gh secret list -R "$REPO" | grep -E "$SEC_KEYSTORE|$SEC_STOREPASS|$SEC_ALIAS|$SEC_KEYPASS" || true

unset STORE_PASS KEY_PASS CONFIRM
printf '\nBACK UP THIS KEYSTORE AND BOTH PASSWORDS SECURELY. Losing them can make future app updates impossible.\n'
printf 'On Termux, copy it to shared storage with:\n'
printf '  termux-setup-storage\n  cp -- "%s" ~/storage/shared/\n\n' "$KS_FILE"
printf 'After the candidate is merged, publish using a vX.Y.Z tag matching app/build.gradle.kts.\n'
