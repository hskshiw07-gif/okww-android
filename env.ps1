# Dot-source this file before running SDK, Gradle or ADB commands.
$ErrorActionPreference = 'Stop'
$RepoRoot = $PSScriptRoot
$ToolsRoot = Join-Path $RepoRoot '.android-tools'
foreach ($folder in @('temp', 'home', 'android-home', 'gradle-home', 'sdk')) {
    New-Item -ItemType Directory -Force (Join-Path $ToolsRoot $folder) | Out-Null
}
$env:TEMP = Join-Path $ToolsRoot 'temp'
$env:TMP = $env:TEMP
$env:TMPDIR = $env:TEMP
$env:HOME = Join-Path $ToolsRoot 'home'
$LocalSdk = Join-Path $ToolsRoot 'sdk'
if ((Test-Path (Join-Path $LocalSdk 'platforms\android-35')) -or -not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = $LocalSdk
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $ToolsRoot 'android-home'
$env:ANDROID_SDK_HOME = $null # Deprecated setting conflicts with ANDROID_USER_HOME in AGP.
$env:GRADLE_USER_HOME = Join-Path $ToolsRoot 'gradle-home'
$env:ANDROID_ADB_LOG_PATH = Join-Path $env:TEMP 'adb.log'
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$($env:HOME) -Djava.io.tmpdir=$($env:TEMP)"
$Jdk = Get-ChildItem -LiteralPath $ToolsRoot -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($Jdk) { $env:JAVA_HOME = $Jdk.FullName }
$env:PATH = "$(Join-Path $ToolsRoot 'platform-tools');$(Join-Path $ToolsRoot 'gradle-8.11.1\bin');$env:PATH"
if ($env:JAVA_HOME) { $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH" }
