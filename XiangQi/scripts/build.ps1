param([switch]$Portable, [switch]$Check)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Publish-VerifiedApk([string]$Source, [string]$Directory) {
    $directory=[IO.Path]::GetFullPath($Directory)
    $expected=[IO.Path]::GetFullPath((Join-Path $projectRoot 'dist'))
    if(-not [string]::Equals($directory,$expected,[StringComparison]::OrdinalIgnoreCase)){throw 'APK delivery must use the exact project dist directory'}
    if(Test-Path -LiteralPath $directory){
        $existing=Get-Item -LiteralPath $directory -Force
        if(-not $existing.PSIsContainer -or ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'APK delivery directory must be an ordinary directory, not a link'}
    }
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $destination=Join-Path $directory 'XiangqiStudy-latest.apk'
    if((Test-Path -LiteralPath $destination) -and ((Get-Item -LiteralPath $destination -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'APK destination must not be a link'}
    $staged=Join-Path $directory ([IO.Path]::GetRandomFileName()+'.apk.tmp')
    try{
        Copy-Item -LiteralPath $Source -Destination $staged
        if((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash){throw 'Staged APK changed'}
        # Same-volume replacement is atomic, including on Windows PowerShell 5.
        # The previous delivery survives any failure before this operation.
        if([IO.File]::Exists($destination)){[IO.File]::Replace($staged,$destination,[NullString]::Value)}else{[IO.File]::Move($staged,$destination)}
        foreach($old in Get-ChildItem -LiteralPath $directory -File -Filter 'XiangqiStudy*.apk'){
            $resolved=[IO.Path]::GetFullPath($old.FullName)
            if(-not [string]::Equals([IO.Path]::GetDirectoryName($resolved),$directory,[StringComparison]::OrdinalIgnoreCase)){throw 'Cleanup escaped the exact APK directory'}
            if(-not [string]::Equals($resolved,$destination,[StringComparison]::OrdinalIgnoreCase)){Remove-Item -LiteralPath $resolved}
        }
        return $destination
    }finally{
        if(Test-Path -LiteralPath $staged){Remove-Item -LiteralPath $staged}
    }
}

if (-not $env:JAVA_HOME -and (Test-Path 'C:\Dev\Tools\jdk-21.0.12.1+1\bin\java.exe')) { $env:JAVA_HOME='C:\Dev\Tools\jdk-21.0.12.1+1' }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME='C:\Dev\Tools\gradle-cache' }
$gradle = if (Test-Path 'C:\Dev\Tools\gradle-8.11.1\bin\gradle.bat') { 'C:\Dev\Tools\gradle-8.11.1\bin\gradle.bat' } else { Join-Path $projectRoot 'gradlew.bat' }
Push-Location $projectRoot
try {
    $buildArgs=@('clean','assembleDebug','--console=plain','--max-workers=2')
    if($Check){$buildArgs+=@('lintDebug','assembleDebugAndroidTest')}
    if($Portable){$buildArgs+='-PwithoutNative=true'}
    & $gradle @buildArgs
    if($LASTEXITCODE -ne 0){throw "Android build failed ($LASTEXITCODE)"}
    $built=Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $verifyArgs=@('scripts/verify-apk.py',$built)
    if($Portable){$verifyArgs+='--portable'}else{$verifyArgs+='--require-latest'}
    & python @verifyArgs
    if($LASTEXITCODE -ne 0){throw 'APK payload verification failed'}
    & 'C:\Dev\Tools\android-sdk\build-tools\35.0.0\apksigner.bat' verify --verbose $built
    if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
    if($Portable){
        $directory=Join-Path $projectRoot 'test-results\apk-audit'
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $destination=Join-Path $directory 'portable.apk'
        Copy-Item -LiteralPath $built -Destination $destination
    }else{
        $destination=Publish-VerifiedApk -Source $built -Directory (Join-Path $projectRoot 'dist')
    }
    Get-FileHash -LiteralPath $destination -Algorithm SHA256 | Format-List
    Write-Output "APK: $destination"
}finally{Pop-Location}
