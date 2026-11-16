#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v cargo >/dev/null 2>&1 || ! command -v rustc >/dev/null 2>&1; then
    printf '%s\n' "Rust and Cargo are required. Install the stable Rust toolchain first."
    exit 1
fi

if ! command -v cargo-ndk >/dev/null 2>&1 && ! cargo ndk --help >/dev/null 2>&1; then
    printf '%s\n' "cargo-ndk is required. Install it once with: cargo install cargo-ndk --locked"
    exit 1
fi

if ! rustup target list --installed | grep -qx 'aarch64-linux-android'; then
    rustup target add aarch64-linux-android
fi

if [[ -z "${ANDROID_NDK_HOME:-}" && -n "${ANDROID_NDK_ROOT:-}" ]]; then
    ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
fi

if [[ -n "${ANDROID_NDK_HOME:-}" ]] && command -v cygpath >/dev/null 2>&1; then
    ANDROID_NDK_HOME="$(cygpath -u "$ANDROID_NDK_HOME")"
fi

# Prints the newest version directory inside "$1". BSD sort on macOS has no
# -V flag, so fall back to a plain lexicographic sort there.
pick_latest_dir() {
    if printf '' | sort -V >/dev/null 2>&1; then
        find "$1" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1
    else
        find "$1" -mindepth 1 -maxdepth 1 -type d | LC_ALL=C sort | tail -n 1
    fi
}

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [[ -z "$SDK_ROOT" ]]; then
        case "$(uname -s)" in
            MINGW*|MSYS*|CYGWIN*) SDK_ROOT="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk" ;;
            Darwin*) SDK_ROOT="$HOME/Library/Android/sdk" ;;
            Linux*) SDK_ROOT="$HOME/Android/Sdk" ;;
        esac
    fi
    if [[ -n "$SDK_ROOT" ]]; then
        if command -v cygpath >/dev/null 2>&1; then
            SDK_ROOT="$(cygpath -u "$SDK_ROOT")"
        fi
        NDK_ROOT="$SDK_ROOT/ndk"
        if [[ -d "$NDK_ROOT" ]]; then
            ANDROID_NDK_HOME="$(pick_latest_dir "$NDK_ROOT")"
        fi
    fi
fi

if [[ -z "${ANDROID_NDK_HOME:-}" || ! -d "$ANDROID_NDK_HOME" ]]; then
    printf '%s\n' "Android NDK was not found. Set ANDROID_NDK_HOME or ANDROID_SDK_ROOT."
    exit 1
fi

export ANDROID_NDK_HOME
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"

HOST_LIBRARY_REL="target/release/rovia_uniffi.dll"
case "${OSTYPE:-}" in
    darwin*) HOST_LIBRARY_REL="target/release/librovia_uniffi.dylib" ;;
    linux*) HOST_LIBRARY_REL="target/release/librovia_uniffi.so" ;;
esac
HOST_LIBRARY="rust/$HOST_LIBRARY_REL"

KOTLIN_SOURCE_ROOT="app/src/main/kotlin"
KOTLIN_PACKAGE_PATH="com/rovia/music/uniffi"
GENERATED_BINDINGS="$KOTLIN_SOURCE_ROOT/$KOTLIN_PACKAGE_PATH/rovia_uniffi.kt"
GENERATED_PACKAGE_DIR="$KOTLIN_SOURCE_ROOT/$KOTLIN_PACKAGE_PATH"

STAMP_FILE="rust/target/.rovia-build-stamp"
ANDROID_LIBRARY="app/src/main/jniLibs/arm64-v8a/librovia_uniffi.so"

compute_input_hash() {
    local hasher="sha256sum"
    if ! command -v sha256sum >/dev/null 2>&1; then
        hasher="shasum -a 256"
    fi
    {
        find rust -path rust/target -prune -o -type f \( -name '*.rs' -o -name 'Cargo.toml' -o -name 'uniffi.toml' \) -print | LC_ALL=C sort
        printf '%s\n' "rust/Cargo.lock"
        printf '%s\n' "scripts/build-rust.sh"
    } | while IFS= read -r entry; do
        $hasher "$entry"
    done | $hasher | awk '{print $1}'
}

CURRENT_HASH="$(compute_input_hash)"

if [[ "${ROVIA_FORCE_CLEAN:-0}" == "1" ]]; then
    printf '%s\n' "ROVIA_FORCE_CLEAN=1: cleaning the Rust workspace..."
    cargo clean --manifest-path rust/Cargo.toml
    rm -f "$STAMP_FILE"
elif [[ -f "$STAMP_FILE" && -f "$ANDROID_LIBRARY" && -f "$GENERATED_BINDINGS" && "$(cat "$STAMP_FILE")" == "$CURRENT_HASH" ]]; then
    printf '%s\n' "No Rust changes detected; the native library and Kotlin bindings are current. Skipping the rebuild."
    printf '%s\n' "Set ROVIA_FORCE_CLEAN=1 to force a full rebuild."
    exit 0
fi

printf '%s\n' "Building incrementally; Cargo recompiles only the crates that changed."
rm -rf "$GENERATED_PACKAGE_DIR"
mkdir -p "$KOTLIN_SOURCE_ROOT" app/src/main/jniLibs/arm64-v8a

if grep -RInE 'pub[[:space:]]+fn[[:space:]]+close[[:space:]]*\(|fn[[:space:]]+close[[:space:]]*\(' rust/direct-usb/src; then
    printf '%s\n' "A Rust close() method is still declared in the Direct USB source. Rename/remove it; UniFFI already supplies AutoCloseable.close()."
    exit 1
fi

printf '%s\n' "Running the host-side descriptor parser tests..."
cargo test --manifest-path rust/Cargo.toml --package rovia-uniffi --lib

printf '%s\n' "Building the host library for UniFFI metadata generation..."
cargo build --manifest-path rust/Cargo.toml --package rovia-uniffi --lib --release

if [[ ! -f "$HOST_LIBRARY" ]]; then
    printf '%s\n' "Expected host library was not produced: $HOST_LIBRARY"
    exit 1
fi

printf '%s\n' "Generating Kotlin bindings from the host library..."
(
    cd "$ROOT_DIR/rust"
    cargo run --manifest-path Cargo.toml --package rovia-bindgen --bin uniffi-bindgen -- generate --library "$HOST_LIBRARY_REL" --language kotlin --out-dir "../$KOTLIN_SOURCE_ROOT" --no-format
)

if [[ ! -f "$GENERATED_BINDINGS" ]]; then
    printf '%s\n' "UniFFI did not generate the expected Kotlin binding: $GENERATED_BINDINGS"
    printf '%s\n' "Generated Kotlin files under the expected package directory:"
    find app/src/main -type f -name 'rovia_uniffi.kt' -print || true
    exit 1
fi

# The UniFFI generator emits trailing whitespace; normalize the binding so
# the generated file stays clean in version control.
sed 's/[ \t]*$//' "$GENERATED_BINDINGS" > "$GENERATED_BINDINGS.tmp"
printf '%s\n' "$(cat "$GENERATED_BINDINGS.tmp")" > "$GENERATED_BINDINGS"
rm -f "$GENERATED_BINDINGS.tmp"

if grep -Fq 'fn_method_usbdirectsession_close' "$GENERATED_BINDINGS"; then
    printf '%s\n' "The generated API still exports UsbDirectSession.close()."
    printf '%s\n' "Only UniFFI's AutoCloseable.close() may exist. Generated declaration:"
    grep -n -B 4 -A 5 'fn_method_usbdirectsession_close' "$GENERATED_BINDINGS" || true
    printf '%s\n' "Search Direct USB source for exported close methods:"
    grep -RInE 'close|UsbDirectSession' rust/direct-usb/src || true
    exit 1
fi

printf '%s\n' "Verified that UsbDirectSession does not export a conflicting close(); UniFFI owns AutoCloseable.close()."
printf '%s\n' "Building the Android arm64-v8a native library..."
rm -f "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/librovia_direct_usb.so"
(
    cd "$ROOT_DIR/rust"
    cargo ndk -t arm64-v8a --platform 33 -o ../app/src/main/jniLibs build --package rovia-uniffi --lib --release
)

if [[ ! -f "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/librovia_uniffi.so" ]]; then
    printf '%s\n' "The Android arm64-v8a library was not produced."
    exit 1
fi

mkdir -p rust/target
printf '%s\n' "$CURRENT_HASH" > "$STAMP_FILE"
printf '%s\n' "Rust and UniFFI build completed for arm64-v8a."
