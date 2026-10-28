# Rovia Music

Rovia Music is a lightweight, native Kotlin and Jetpack Compose Android music player with a beautiful Material 3 Expressive UI.

## Requirements

- [Eclipse Temurin JDK 25 LTS](https://adoptium.net/temurin/releases/?version=25)
- Android SDK Platform 37.1
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

### 2. Configure the Android SDK

Configure the Android SDK and its environment variables by following the [official Android command-line tools documentation](https://developer.android.com/tools).

Install Android SDK Platform 37.1 and the SDK tools required by the project. Compose UI `1.13.0-beta02` requires compiling against Android API 37.1 or later. The project uses API 37.1 for compilation while retaining target SDK 37.

### 3. Verify the Build Toolchain

Clone the repository, then verify the configured Gradle and Java versions using the Gradle Wrapper:

```bash
./gradlew --version
```

On Windows, use `gradlew.bat` instead.

### 4. Clone the Repository

```bash
git clone https://github.com/valleriankondengis/rovia-music.git
cd rovia-music
```

### 5. Build the Debug APK

Run the following command from the project root:

```bash
./gradlew :app:assembleDebug
```

On Windows, use:

```powershell
.\gradlew.bat :app:assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project Configuration

| Setting | Value |
|---|---|
| Language | Kotlin |
| UI Toolkit | Jetpack Compose |
| Design System | Material 3 Expressive |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.8.2 |
| JDK | 25 LTS |
| Compile SDK | 37.1 (`compileSdk = 37`, `compileSdkMinor = 1`) |
| Minimum SDK | 33 |
| Target SDK | 37 |
| Application ID | `com.rovia.music` |