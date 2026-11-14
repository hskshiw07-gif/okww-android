param([switch]$TestOnly, [switch]$Fixtures)
. "$PSScriptRoot\env.ps1"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'JDK 17 is required; set JAVA_HOME or place a jdk-* directory under .android-tools.'
}
if (-not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-35'))) {
    throw 'Android SDK platform 35 is required; set ANDROID_HOME or install it under .android-tools\sdk.'
}

$Gradle = Join-Path $ToolsRoot 'gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $Gradle)) {
    if (Get-Command gradle.bat -ErrorAction SilentlyContinue) { $Gradle = 'gradle.bat' }
    elseif (Get-Command gradle -ErrorAction SilentlyContinue) { $Gradle = 'gradle' }
    else { throw 'Gradle 8.11.1 is required; install it under .android-tools or add Gradle to PATH.' }
}

Push-Location $PSScriptRoot
try {
    if ($TestOnly) { & $Gradle --no-daemon testDebugUnitTest }
    elseif ($Fixtures) { & $Gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest }
    else { & $Gradle --no-daemon testDebugUnitTest lintDebug assembleDebug }
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
    if (-not $TestOnly) {
        $Output = Join-Path $PSScriptRoot 'dist'
        New-Item -ItemType Directory -Force $Output | Out-Null
        $Apk = Join-Path $Output 'okww-android-debug.apk'
        Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination $Apk -Force
        $Hash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
        [System.IO.File]::WriteAllText("$Apk.sha256", "$Hash  okww-android-debug.apk`n")
        Write-Output "APK: $Apk"
    }
} finally { Pop-Location }
