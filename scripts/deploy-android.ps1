param(
    [Parameter(Mandatory = $true)]
    [string] $Dk2Path,

    [string] $AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",

    [switch] $SkipConversion,

    [switch] $SkipAssets
)

$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $AndroidSdk "platform-tools\adb.exe"
$gradle = Join-Path $workspace "gradlew.bat"
$packageName = "toniarts.openkeeper.android.debug"
$remoteFiles = "/sdcard/Android/data/$packageName/files"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb was not found at $adb"
}
if (-not (Test-Path -LiteralPath (Join-Path $Dk2Path "Data"))) {
    throw "Dungeon Keeper 2 Data folder was not found below $Dk2Path"
}

$devices = @(
    & $adb devices |
        Select-String "`tdevice$" |
        ForEach-Object { $_.Line.Split("`t")[0] }
)
if ($devices.Count -ne 1) {
    throw "Connect exactly one authorized Android device. Found $($devices.Count)."
}
$serial = $devices[0]

Push-Location $workspace
try {
    if (-not $SkipConversion) {
        & $gradle convertAssetsForAndroid "-Pdk2Folder=$Dk2Path" --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "DK2 asset conversion failed."
        }
    }

    & $gradle :android:assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed."
    }

    & $adb -s $serial install -r "android\build\outputs\apk\debug\android-debug.apk"
    if ($LASTEXITCODE -ne 0) {
        throw "APK installation failed."
    }

    if (-not $SkipAssets) {
        & $adb -s $serial shell mkdir -p "$remoteFiles/Converted" "$remoteFiles/DK2"
        & $adb -s $serial push --sync "assets\Converted\." "$remoteFiles/Converted/"
        if ($LASTEXITCODE -ne 0) {
            throw "Converted asset upload failed."
        }

        & $adb -s $serial push --sync (Join-Path $Dk2Path "Data\.") "$remoteFiles/DK2/"
        if ($LASTEXITCODE -ne 0) {
            throw "Original DK2 data upload failed."
        }
    }

    & $adb -s $serial shell am force-stop $packageName
    & $adb -s $serial shell am start -n "$packageName/toniarts.openkeeper.android.OpenKeeperAndroidActivity"
    if ($LASTEXITCODE -ne 0) {
        throw "OpenKeeper launch failed."
    }
}
finally {
    Pop-Location
}

