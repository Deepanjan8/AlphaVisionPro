# Android Development & Native compilation Guardrails

## 1. Biometric Authentication & FragmentActivity
* **Constraint**: When launching a `BiometricPrompt` inside Jetpack Compose, the system context must be castable to `FragmentActivity`.
* **Guardrail**: Ensure that `MainActivity` inherits from `androidx.fragment.app.FragmentActivity` instead of `androidx.activity.ComponentActivity` or `androidx.appcompat.app.AppCompatActivity` if any downstream screens (such as secure vaults) use biometrics.

## 2. Dependency Injection & Transitive Classpaths
* **Constraint**: In multi-module Android projects, if the `:app` module has Dagger Hilt `@Module` providers returning third-party classes (e.g., `RoomDatabase`, `ImageLoader`), the `:app` module must explicitly declare those dependencies in its `build.gradle.kts`.
* **Guardrail**: Do not rely on transitive compile classpaths via `implementation(project(":feature"))` because `implementation` dependencies are not exposed transitively on the compiler classpath of depending modules.

## 3. Coil 3 Disk Cache Directory configuration
* **Constraint**: Coil 3 utilizes `okio.Path` for its multiplatform disk cache configurations. Passing a standard `java.io.File` will result in a compiler error.
* **Guardrail**: Import `okio.Path.Companion.toPath` and convert `File` objects or path strings using `.absolutePath.toPath()` or `.toPath()`.

## 4. Rust JNI library Compilation for Android
* **Constraint**: When utilizing a Rust-based native image processing or utility layer, integrate compilation into the CI/CD pipeline using `cargo-ndk`.
* **Best Practice**:
  - Add targets `aarch64-linux-android` and `x86_64-linux-android` via rustup.
  - Compile using `cargo ndk -t arm64-v8a -t x86_64 -o <module>/src/main/jniLibs build --release`.
  - Avoid complex Gradle/CMake integrations; Gradle will automatically package output `.so` binaries from the `jniLibs` folder.
