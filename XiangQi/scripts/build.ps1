param([switch]$Portable, [switch]$Check)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Dev\Tools\jdk-21.0.12.1+1\bin\java.exe')) {
    $env:JAVA_HOME = 'C:\Dev\Tools\jdk-21.0.12.1+1'
}
Push-Location $projectRoot
try {
    $buildArgs = @('clean', 'assembleDebug', '--console=plain')
    if ($Check) { $buildArgs += @('lintDebug', 'assembleDebugAndroidTest') }
    if ($Portable) { $buildArgs += '-PwithoutNative=true' }
    & .\gradlew.bat @buildArgs
    if ($LASTEXITCODE -ne 0) { throw "Android build failed ($LASTEXITCODE)" }
    New-Item -ItemType Directory -Force dist | Out-Null
    $variant = if ($Portable) { 'portable' } else { 'arm64' }
    $metadata = Get-Content -Raw -LiteralPath app\build\outputs\apk\debug\output-metadata.json | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    $apkPath = Join-Path $projectRoot "dist\XiangqiStudy-$version-android12-$variant.apk"
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination $apkPath
    $latestName = if ($Portable) { 'XiangqiStudy-portable-latest.apk' } else { 'XiangqiStudy-latest.apk' }
    Copy-Item -LiteralPath $apkPath -Destination (Join-Path $projectRoot "dist\$latestName")
    Get-FileHash -LiteralPath $apkPath -Algorithm SHA256 | Format-List
    Write-Output "APK: $apkPath"
    Write-Output "Latest APK: $(Join-Path $projectRoot "dist\$latestName")"
} finally { Pop-Location }
