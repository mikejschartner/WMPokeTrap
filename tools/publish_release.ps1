param(
    [string]$Tag = "",
    [string]$VersionName = "",
    [int]$VersionCode = 0
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Gh = Join-Path $Root "tools\gh\bin\gh.exe"
if (-not (Test-Path $Gh)) {
    throw "GitHub CLI missing at $Gh"
}

$Gradle = Join-Path $Root "app\build.gradle.kts"
$GradleText = Get-Content $Gradle -Raw
if ($VersionCode -le 0) {
    if ($GradleText -match 'versionCode\s*=\s*(\d+)') { $VersionCode = [int]$Matches[1] }
}
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    if ($GradleText -match 'versionName\s*=\s*"([^"]+)"') { $VersionName = $Matches[1] }
}
if ([string]::IsNullOrWhiteSpace($Tag)) {
    $Tag = "v$VersionName"
}

$ApkSrc = Join-Path $env:TEMP "wmpoketrap-app-build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $ApkSrc)) {
    throw "APK not found. Build first: gradlew.bat :app:assembleDebug"
}

$ReleaseDir = Join-Path $Root "release"
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$ApkOut = Join-Path $ReleaseDir "WMPokeTrap.apk"
$JsonOut = Join-Path $ReleaseDir "latest.json"
Copy-Item $ApkSrc $ApkOut -Force
Copy-Item $ApkSrc (Join-Path $Root "WMPokeTrap-debug.apk") -Force
Copy-Item $ApkSrc (Join-Path ([Environment]::GetFolderPath('Desktop')) "WMPokeTrap-debug.apk") -Force -ErrorAction SilentlyContinue

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$jsonBody = "{`n  `"versionCode`": $VersionCode,`n  `"versionName`": `"$VersionName`"`n}`n"
[System.IO.File]::WriteAllText($JsonOut, $jsonBody, $utf8NoBom)

$Notes = @"
versionCode=$VersionCode

WM PokeTrap $VersionName

- Optional False Swipe (skip Fight/HP when off)
- Approved ball types + priority order
- Stop when selected ball unavailable (no auto Great/Ultra swap)
- Bag OCR verifies ball before throw
- Humanize inputs toggle (random timing / aim jitter)
- Catch retry limit / safe stop on errors
- Install over previous build (keeps settings)
- Setup tab → Check Update / Install Update
"@

$ErrorActionPreference = "Continue"
& $Gh release view $Tag -R mikejschartner/WMPokeTrap 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    & $Gh release delete $Tag -R mikejschartner/WMPokeTrap --yes
}
$ErrorActionPreference = "Stop"

& $Gh release create $Tag `
    -R mikejschartner/WMPokeTrap `
    --title "WM PokeTrap $VersionName" `
    --notes $Notes `
    $ApkOut `
    $JsonOut

Write-Host "Published $Tag -> https://github.com/mikejschartner/WMPokeTrap/releases/tag/$Tag"
