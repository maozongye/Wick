#!/usr/bin/env sh
# Reports whether this machine can build WeKit. Read-only: it never installs or modifies anything.
#
#   sh scripts/check-build-env.sh
#
# Exit code 0 when everything required for `./x build` is present, 1 otherwise.

set -u

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root" || exit 1

missing=0
warn=0

ok()   { printf '  [ok]      %s\n' "$1"; }
bad()  { printf '  [MISSING] %s\n' "$1"; missing=$((missing + 1)); }
note() { printf '  [warn]    %s\n' "$1"; warn=$((warn + 1)); }

version_ge() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 >= b + 0) }'; }

echo "WeKit build environment"
echo "-----------------------"

# ── JDK ───────────────────────────────────────────────────────────────────────
echo "JDK (21 required by gradle/libs.versions.toml:jdk)"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java_bin="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    java_bin=$(command -v java)
else
    java_bin=""
fi
if [ -z "$java_bin" ]; then
    bad "java not found (install JDK 21 and set JAVA_HOME)"
else
    java_version=$("$java_bin" -version 2>&1 | awk -F'"' '/version/ { print $2 }')
    java_major=$(echo "$java_version" | awk -F. '{ if ($1 == 1) print $2; else print $1 }')
    if [ "$java_major" = "21" ]; then
        ok "JDK $java_major ($java_bin)"
    else
        bad "JDK $java_major at $java_bin — project requires 21"
    fi
fi

# ── Android SDK ───────────────────────────────────────────────────────────────
echo "Android SDK (compileSdk 37, build-tools, platform-tools)"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk=$(sed -n 's/^sdk\.dir=//p' local.properties | tail -1)
fi
if [ -z "$sdk" ]; then
    bad "ANDROID_HOME unset and local.properties has no sdk.dir"
else
    ok "SDK at $sdk"
    # AGP 9 names the platform dir android-<major>.<minor>; compileSdkMinor=0 gives "android-37.0"
    if ls -d "$sdk"/platforms/android-37* >/dev/null 2>&1; then
        ok "platform $(ls "$sdk"/platforms/android-37* | sort -V | tail -1 | awk -F/ '{ print $NF }')"
    else
        bad "platform android-37 (sdkmanager 'platforms;android-37')"
    fi
    if [ -d "$sdk/build-tools" ] && [ -n "$(ls -A "$sdk/build-tools" 2>/dev/null)" ]; then
        ok "build-tools: $(ls "$sdk/build-tools" | sort -V | tail -1)"
    else
        bad "build-tools (sdkmanager 'build-tools;37.0.0')"
    fi
    if [ -d "$sdk/platform-tools" ]; then
        ok "platform-tools"
    else
        note "platform-tools absent (only needed for install/deploy)"
    fi
fi

# ── NDK + CMake (native lib; xtask task_build_native) ────────────────────────
echo "NDK 30.0.14904198 + CMake (native library)"
if [ -n "$sdk" ] && [ -d "$sdk/ndk/30.0.14904198" ]; then
    ok "NDK 30.0.14904198"
else
    bad "NDK 30.0.14904198 (sdkmanager 'ndk;30.0.14904198')"
fi
if command -v cmake >/dev/null 2>&1; then
    cmake_ver=$(cmake --version | awk '/version/ { print $3 }')
    if version_ge "$cmake_ver" 3.28; then
        ok "cmake $cmake_ver"
    else
        bad "cmake $cmake_ver — wekit-zygisk requires >= 3.28"
    fi
elif [ -n "$sdk" ] && [ -d "$sdk/cmake" ]; then
    ok "cmake from SDK: $(ls "$sdk/cmake" | sort -V | tail -1)"
else
    bad "cmake >= 3.28 (wekit-zygisk/native/CMakeLists.txt)"
fi

# ── Rust ──────────────────────────────────────────────────────────────────────
echo "Rust toolchain (aarch64-linux-android)"
if command -v cargo >/dev/null 2>&1; then
    ok "cargo $(cargo --version | awk '{ print $2 }')"
    if rustup target list --installed 2>/dev/null | grep -qx aarch64-linux-android; then
        ok "target aarch64-linux-android"
    else
        bad "target aarch64-linux-android (rustup target add aarch64-linux-android)"
    fi
    if command -v cargo-ndk >/dev/null 2>&1; then
        ok "cargo-ndk"
    else
        note "cargo-ndk absent (only needed if xtask shells out to it)"
    fi
else
    bad "cargo (install Rust via rustup)"
fi

# ── Submodules ────────────────────────────────────────────────────────────────
echo "Git submodules (scripta is an includeBuild — build fails while empty)"
if [ -f .gitmodules ]; then
    uninit=$(git submodule status 2>/dev/null | awk '/^-/ { print $2 }')
    if [ -z "$uninit" ]; then
        ok "all submodules initialised"
    else
        bad "uninitialised: $(echo "$uninit" | tr '\n' ' ')"
        printf '            run: git submodule update --init --recursive\n'
    fi
else
    note ".gitmodules not found — skipping"
fi

# ── Gradle wrapper ────────────────────────────────────────────────────────────
echo "Gradle wrapper"
if grep -q 'gradle-9' gradle/wrapper/gradle-wrapper.properties 2>/dev/null; then
    ok "wrapper $(sed -n 's/.*gradle-\([0-9.]*\)-bin.*/\1/p' gradle/wrapper/gradle-wrapper.properties)"
else
    note "unexpected wrapper distribution"
fi

# ── Optional: dex-test / extensions ──────────────────────────────────────────
echo "Optional (only for ./x dex-test and ./x extensions)"
if command -v uv >/dev/null 2>&1; then ok "uv"; else note "uv absent (extension packs)"; fi
if command -v adb >/dev/null 2>&1; then ok "adb"; else note "adb absent (install/deploy)"; fi

echo "-----------------------"
if [ "$missing" -eq 0 ]; then
    echo "Ready: $missing missing, $warn warnings."
    exit 0
fi
echo "Not ready: $missing missing, $warn warnings."
exit 1
