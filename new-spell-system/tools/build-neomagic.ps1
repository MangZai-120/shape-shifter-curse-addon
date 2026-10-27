param(
    [string[]]$Tasks = @('neoMagicTest', 'neoMagicIntegrationTest', 'build'),
    [switch]$Isolated
)
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$javaPath = 'C:\Program Files\Java\jdk-17'
if (-not (Test-Path -LiteralPath (Join-Path $javaPath 'bin/java.exe'))) { throw 'Install JDK 17 or adapt javaPath in this development helper.' }
$gradleHome = Join-Path $env:USERPROFILE '.gradle'
$gradleCommand = Get-ChildItem -LiteralPath (Join-Path $gradleHome 'wrapper/dists/gradle-8.11.1-bin') -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
if (-not $gradleCommand) { throw 'This Loom version requires Gradle 8.11.1. Install/cache that distribution before building.' }
$env:JAVA_HOME = $javaPath
$buildArguments = @('--offline', '--no-daemon', '--console=plain', '--gradle-user-home', $gradleHome)
if ($Isolated) {
    $cache = Join-Path $repoPath 'build/neomagic/isolated-gradle'
    if (-not (Test-Path -LiteralPath (Join-Path $cache 'caches/modules-2'))) { throw 'No isolated cache prepared. Run normally or prepare an isolated development cache first.' }
    $buildArguments = @('--offline','--no-daemon','--console=plain','--gradle-user-home',$cache,'--project-cache-dir',(Join-Path $repoPath 'build/neomagic/project-cache'))
}
Push-Location -LiteralPath $repoPath
try {
    & $gradleCommand @buildArguments @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Neomagic build failed: $LASTEXITCODE" }
} finally { Pop-Location }
