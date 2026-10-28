param([switch]$Portable, [switch]$Check)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'C:\Dev\Tools\jdk-21.0.12.1+1\bin\java.exe')) {
    $env:JAVA_HOME = 'C:\Dev\Tools\jdk-21.0.12.1+1'
}
Push-Location $projectRoot
try {
    $buildArgs = @('assembleDebug', '--console=plain')
    if ($Check) { $buildArgs += @('lintDebug', 'assembleDebugAndroidTest') }
    if ($Portable) { $buildArgs += '-PwithoutNative=true' }
    & .\gradlew.bat @buildArgs
    if ($LASTEXITCODE -ne 0) { throw "Android build failed ($LASTEXITCODE)" }
    New-Item -ItemType Directory -Force dist | Out-Null
    $variant = if ($Portable) { 'portable' } else { 'arm64' }
    $apkPath = Join-Path $projectRoot "dist\XiangqiStudy-0.1.0-android12-$variant.apk"
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination $apkPath
    Get-FileHash -LiteralPath $apkPath -Algorithm SHA256 | Format-List
    Write-Output "APK: $apkPath"
} finally { Pop-Location }
