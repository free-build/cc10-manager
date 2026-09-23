$ErrorActionPreference = 'Stop'

$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $Project
$Sdk = $env:ANDROID_SDK_ROOT
$SignerJar = $env:UBER_APK_SIGNER_JAR
if (-not $Sdk) { throw 'Set ANDROID_SDK_ROOT first.' }
if (-not $SignerJar) { throw 'Set UBER_APK_SIGNER_JAR first.' }

$AndroidJar = Join-Path $Sdk 'platforms\android-27\android.jar'
$BuildTools = Join-Path $Sdk 'build-tools\28.0.3'
$Aapt = Join-Path $BuildTools 'aapt.exe'
$DxJar = Join-Path $BuildTools 'lib\dx.jar'
$ZipAlign = Join-Path $BuildTools 'zipalign.exe'
$Java = (Get-Command java).Source
$Javac = (Get-Command javac).Source
$Jar = (Get-Command jar).Source

foreach ($Required in @($AndroidJar, $Aapt, $DxJar, $ZipAlign, $SignerJar,
        $Java, $Javac, $Jar)) {
    if (-not (Test-Path -LiteralPath $Required)) { throw "Missing: $Required" }
}

$Build = Join-Path $Project 'build'
$Classes = Join-Path $Build 'classes'
$Dex = Join-Path $Build 'dex'
$Output = Join-Path $RepoRoot 'release'
if (Test-Path $Build) { Remove-Item -LiteralPath $Build -Recurse -Force }
New-Item -ItemType Directory -Path $Classes, $Dex, $Output -Force | Out-Null

$CompilerAndroidJar = Join-Path $Build 'android.jar'
Copy-Item -LiteralPath $AndroidJar -Destination $CompilerAndroidJar -Force
$Sources = Get-ChildItem (Join-Path $Project 'src') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }

& $Javac -encoding UTF-8 -source 1.7 -target 1.7 -bootclasspath $CompilerAndroidJar `
    -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

$ClassesJar = Join-Path $Build 'classes.jar'
Push-Location $Classes
try { & $Jar cf $ClassesJar . } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

& $Java -jar $DxJar --dex --min-sdk-version=23 `
    "--output=$(Join-Path $Dex 'classes.dex')" $ClassesJar
if ($LASTEXITCODE -ne 0) { throw 'dx failed' }

$Unsigned = Join-Path $Build 'CC10ManagerLauncher-unsigned.apk'
& $Aapt package -f -M (Join-Path $Project 'AndroidManifest.xml') `
    -S (Join-Path $Project 'res') -I $AndroidJar -F $Unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }

Push-Location $Dex
try { & $Aapt add $Unsigned 'classes.dex' } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'aapt add failed' }

$Aligned = Join-Path $Build 'CC10ManagerLauncher-aligned.apk'
& $ZipAlign -f 4 $Unsigned $Aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$SignerHome = Join-Path $env:TEMP 'cc10-manager-launcher-signer-home'
$SignerOutput = Join-Path $Build 'signed'
New-Item -ItemType Directory -Path $SignerHome, $SignerOutput -Force | Out-Null
$OldProfile = $env:USERPROFILE
$OldHome = $env:HOME
$OldAndroidHome = $env:ANDROID_USER_HOME
try {
    # Force uber-apk-signer to use its embedded Android test key. This matches v1.0.
    $env:USERPROFILE = $SignerHome
    $env:HOME = $SignerHome
    $env:ANDROID_USER_HOME = $SignerHome
    & $Java "-Duser.home=$SignerHome" -jar $SignerJar --apks $Aligned `
        --out $SignerOutput --allowResign
} finally {
    $env:USERPROFILE = $OldProfile
    $env:HOME = $OldHome
    $env:ANDROID_USER_HOME = $OldAndroidHome
}
if ($LASTEXITCODE -ne 0) { throw 'signing failed' }

$Signed = Get-ChildItem $SignerOutput -Filter '*debugSigned.apk' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $Signed) { throw 'signed APK not found' }
$Final = Join-Path $Output 'CC10-Optimization-Manager-Shortcut-v1.1.apk'
Copy-Item -LiteralPath $Signed.FullName -Destination $Final -Force
Get-FileHash $Final -Algorithm SHA256
