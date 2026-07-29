# WM PokeTrap Releases

Public release host for the WM PokeTrap Android app.

The phone app checks this repository's **latest GitHub Release** and can download/install updates in place (settings kept).

## For Mike (publishing a new build)

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`
2. Build: `gradlew.bat :app:assembleDebug`
3. Update `release/latest.json` to match
4. Publish:

```powershell
.\tools\publish_release.ps1
```

Or manually with `gh release create`.
