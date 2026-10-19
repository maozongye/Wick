#!/usr/bin/env bash
# Static fingerprint scan for a built WeKit APK (HARDENING.md principle 1).
#
# Usage: sh scripts/check-features.sh <apk>
# Exit 0 = clean (no module-identifying constants), exit 1 = fingerprints found.
#
# This is the CI gate: run it right after ./x build --release and treat any hit
# as a build failure, not a warning — a leaked constant is a sample signature
# every detector can reuse.
set -u

APK="${1:?usage: check-features.sh <apk>}"
[ -f "$APK" ] || { echo "FAIL: APK not found: $APK"; exit 1; }

fail=0

# Layer 1: DEX must not contain the source package namespace.
if unzip -p "$APK" 'classes*.dex' 2>/dev/null | strings | grep -ai -E 'dev/ujhhgtg|dev\.ujhhgtg' | head -5 | grep -q .; then
    echo "FAIL: dex contains dev.ujhhgtg class names (R8 renaming incomplete?)"
    unzip -p "$APK" 'classes*.dex' 2>/dev/null | strings | grep -ai -E 'dev/ujhhgtg|dev\.ujhhgtg' | head -5
    fail=1
else
    echo "PASS: dex free of dev.ujhhgtg"
fi

# Layer 2: DEX must not contain the user-visible product tag as a lookup constant.
# ("WeKit" may legitimately survive in UI label strings; flag only when bundled
# with the namespace, which always indicates a code path, not copy.)
if unzip -p "$APK" 'classes*.dex' 2>/dev/null | strings | grep -aiE 'wekit' | grep -av 'WeKit' | head -5 | grep -q .; then
    echo "WARN: dex contains lowercase 'wekit' constants — review manually"
    unzip -p "$APK" 'classes*.dex' 2>/dev/null | strings | grep -aiE 'wekit' | grep -av 'WeKit' | head -5
fi

# Layer 3: native libraries shipped in the APK.
for so in $(unzip -l "$APK" 'lib/*/*.so' 2>/dev/null | awk '{print $4}' | grep '\.so$'); do
    # libwick_native.so / libwick_zygisk.so file names are expected (loaded by fixed name
    # from the APK); their CONTENT must not embed the namespace.
    if unzip -p "$APK" "$so" 2>/dev/null | strings | grep -ai -E 'dev/ujhhgtg|dev\.ujhhgtg' | head -3 | grep -q .; then
        echo "FAIL: $so embeds dev.ujhhgtg strings"
        fail=1
    else
        echo "PASS: $so clean"
    fi
done

# Layer 4: the package name must carry the derived suffix (not the published id).
suffix=$(unzip -p "$APK" AndroidManifest.xml 2>/dev/null | strings | grep -aoE 'dev\.ujhhgtg\.wekit\.[0-9a-f]{8}' | head -1)
if [ -n "$suffix" ]; then
    echo "PASS: application id derived ($suffix)"
else
    echo "WARN: application id not derived — built with -PwekitStablePackage=true?"
fi

if [ "$fail" -ne 0 ]; then
    echo "RESULT: FAIL"
    exit 1
fi
echo "RESULT: PASS"
