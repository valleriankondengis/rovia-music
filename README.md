# Rovia Music

Rovia Music is a lightweight, native Kotlin and Jetpack Compose Android music player with a beautiful Material 3 Expressive UI.

## Requirements

- [Eclipse Temurin JDK 25 LTS](https://adoptium.net/temurin/releases/?version=25)
- Android SDK Platform 37.1
- [Rust](https://rustup.rs/) (stable toolchain via rustup)
- Git

Rovia uses Gradle Wrapper to manage its Gradle version. A separate Gradle installation is not required.

## Development Setup

### 1. Install JDK 25

Download and install Eclipse Temurin JDK 25 LTS from the [official Adoptium website](https://adoptium.net/temurin/releases/?version=25).

Configure `JAVA_HOME` to point to the JDK installation directory and ensure Java is available in your `PATH`.

Verify the installation:

```bash
java -version
javac -version
```

### 2. Install Rust

Install the stable Rust toolchain with [rustup](https://rustup.rs/). On Linux and macOS:

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

On Windows, run the rustup installer from the same page instead. Then add the Android target and install `cargo-ndk`, which the Rust build script uses to cross-compile the native library:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
```

### 3. Configure the Android SDK

Configure the Android SDK and its environment variables by following the [official Android command-line tools documentation](https://developer.android.com/tools).

Install Android SDK Platform 37.1 and the SDK tools required by the project. Compose UI `1.13.0-beta02` requires compiling against Android API 37.1 or later. The project uses API 37.1 for compilation while retaining target SDK 37.

### 4. Verify the Build Toolchain

Clone the repository, then verify the configured Gradle and Java versions using the Gradle Wrapper:

```bash
./gradlew --version
```

On Windows, use `gradlew.bat` instead.

### 5. Clone the Repository

```bash
git clone https://github.com/valleriankondengis/rovia-music.git
cd rovia-music
```

### 6. Build the Rust and UniFFI Backend

Run the following command from a terminal at the project root (Git Bash UCRT64 on Windows). It runs the host-side descriptor parser tests, generates the Kotlin UniFFI binding, and cross-compiles the Android ARM64 native library. The script works on Windows, Linux and macOS; it finds the Android NDK through `ANDROID_NDK_HOME` or `ANDROID_SDK_ROOT` when set, and otherwise uses the standard SDK location for the operating system.

```bash
bash scripts/build-rust.sh
```

The generated UniFFI Kotlin binding is written to `app/src/main/kotlin/com/rovia/music/uniffi/rovia_uniffi.kt`. The build script regenerates this Kotlin package from the Rust library before the Android native build. Application-owned implementation code is Kotlin and Rust; generated build output and Android resource/configuration files are not additional application languages.

### 7. Build the Debug APK

After the Rust and UniFFI build succeeds, run the following command from the project root:

```bash
./gradlew :app:assembleDebug
```

On Windows (PowerShell), use `.\gradlew.bat :app:assembleDebug` instead.

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project Configuration

| Setting | Value |
|---|---|
| Application languages | Kotlin + Rust |
| UI Toolkit | Jetpack Compose |
| Design System | Material 3 Expressive |
| Android Gradle Plugin | 9.6.0-alpha03 |
| Gradle | 9.8.2 |
| JDK | 25 LTS |
| Rust toolchain | stable via rustup, target `aarch64-linux-android` |
| NDK | 30.0.16248370 |
| Compile SDK | 37.1 (`compileSdk = 37`, `compileSdkMinor = 1`) |
| Minimum SDK | 33 |
| Target SDK | 37 |
| Application ID | `com.rovia.music` |
