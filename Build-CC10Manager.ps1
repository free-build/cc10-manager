$ErrorActionPreference = 'Stop'

$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
$Sdk = $env:ANDROID_SDK_ROOT
$PlatformKeyDir = $env:CC10_PLATFORM_KEY_DIR
if (-not $Sdk) { throw 'Set ANDROID_SDK_ROOT first.' }
if (-not $PlatformKeyDir) { throw 'Set CC10_PLATFORM_KEY_DIR first.' }

$AndroidJar = Join-Path $Sdk 'platforms\android-27\android.jar'
$BuildTools = Join-Path $Sdk 'build-tools\28.0.3'
$Aapt = Join-Path $BuildTools 'aapt.exe'
$DxJar = Join-Path $BuildTools 'lib\dx.jar'
$ZipAlign = Join-Path $BuildTools 'zipalign.exe'
$ApkSigner = Join-Path $BuildTools 'apksigner.bat'
$PlatformCert = Join-Path $PlatformKeyDir 'platform.x509.pem'
$PlatformKey = Join-Path $PlatformKeyDir 'platform.pk8'
$Java = (Get-Command java).Source
$Javac = (Get-Command javac).Source
$Jar = (Get-Command jar).Source

foreach ($Required in @($AndroidJar, $Aapt, $DxJar, $ZipAlign, $ApkSigner,
        $PlatformCert, $PlatformKey, $Java, $Javac, $Jar)) {
    if (-not (Test-Path -LiteralPath $Required)) { throw "Missing: $Required" }
}

$Build = Join-Path $Project 'build'
$Classes = Join-Path $Build 'classes'
$Dex = Join-Path $Build 'dex'
$Output = Join-Path $Project 'release'
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

$Unsigned = Join-Path $Build 'CC10Manager-unsigned.apk'
& $Aapt package -f -M (Join-Path $Project 'AndroidManifest.xml') `
    -S (Join-Path $Project 'res') -I $AndroidJar -F $Unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }

Push-Location $Dex
try { & $Aapt add $Unsigned 'classes.dex' } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'aapt add failed' }

$Aligned = Join-Path $Build 'CC10Manager-aligned.apk'
& $ZipAlign -f 4 $Unsigned $Aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$Final = Join-Path $Output 'CC10-Optimization-Manager-v3.4.apk'
& $ApkSigner sign --key $PlatformKey --cert $PlatformCert --out $Final $Aligned
if ($LASTEXITCODE -ne 0) { throw 'platform signing failed' }
& $ApkSigner verify --verbose --print-certs $Final
Get-FileHash $Final -Algorithm SHA256
