# WMPokeTrap (Android) — CI/CD

GitHub Actions builds the debug APK and can publish Releases.

## Automatic builds (CI)

Push or PR to `main` → Ubuntu runner:

1. JDK 17 + Gradle  
2. `./gradlew :app:assembleDebug`  
3. Upload `WMPokeTrap.apk` + `latest.json`

## Releases (CD)

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`  
2. Commit & push to `main`  
3. Tag and push:

```bash
git tag v1.3.25
git push origin v1.3.25
```

Friends keep using Setup → Check Update (repo name unchanged).

## Manual

Actions → **Android CI/CD** → **Run workflow**
