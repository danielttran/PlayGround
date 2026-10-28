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
& $Java -Xmx512m -cp $Classes org.xiangqi.study.core.DateSortTest
if ($LASTEXITCODE -ne 0) { throw 'Date sort tests failed' }
& $Java -Xmx512m -cp $Classes org.xiangqi.study.core.AppearancePresetsTest
if ($LASTEXITCODE -ne 0) { throw 'Appearance preset tests failed' }
& $Java -Xmx512m -cp $Classes org.xiangqi.study.core.AnalysisVisualsTest
if ($LASTEXITCODE -ne 0) { throw 'Analysis visual tests failed' }
$PackManifest=Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $ProjectRoot 'app\src\main\assets\game-packs.json') | ConvertFrom-Json
$Names=@();$Corpus=@()
foreach($Pack in $PackManifest.packs){
    $Records=(Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $ProjectRoot ('app\src\main\assets\'+$Pack.file)) | ConvertFrom-Json).games
    foreach($Record in $Records){$Names+=$Record.red;$Names+=$Record.black;$Corpus+=($Record.id+"`t"+($Record.moves -join ' '))}
}
$NameFile=Join-Path $Classes 'bundled-names.txt';$Names | Sort-Object -Unique | Set-Content -Encoding utf8 -LiteralPath $NameFile
& $Java -Xmx512m -cp $Classes org.xiangqi.study.core.PlayerNamesTest $NameFile
if ($LASTEXITCODE -ne 0) { throw 'Player name tests failed' }
$CorpusFile=Join-Path $Classes 'bundled-games.tsv';$Corpus | Set-Content -Encoding utf8 -LiteralPath $CorpusFile
& $Java -Xmx512m -cp $Classes org.xiangqi.study.core.ValidateGames $CorpusFile
if ($LASTEXITCODE -ne 0) { throw 'Bundled game replay failed' }
