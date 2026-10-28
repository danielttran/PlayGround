param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) {
    $BundledJava = 'C:\Dev\Tools\jdk-21.0.12.1+1'
    if (Test-Path -LiteralPath "$BundledJava\bin\javac.exe") { $JavaHome = $BundledJava }
}
$Javac = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$Java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
$Classes = Join-Path $ProjectRoot 'build\core-tests'
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
$Sources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'app\src\main\java\org\xiangqi\study\core') -Filter '*.java' | Select-Object -ExpandProperty FullName)
$Tests = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'tests\core') -Filter '*.java' | Select-Object -ExpandProperty FullName)
& $Javac -encoding UTF-8 -Xlint:all -d $Classes @Sources @Tests
if ($LASTEXITCODE -ne 0) { throw 'Core Java compilation failed' }
& $Java -Xmx512m -cp $Classes org.xiangqi.study.core.CoreTest
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
