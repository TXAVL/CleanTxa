# Dựng lại các gói Home từ module :launcher (txahome.apk, tvhome.apk, tvhome-xiaomi.apk) và đưa vào assets của app.
# Chạy từ thư mục gốc dự án:  powershell -File tools\build-txahome.ps1
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$variants = @(
    @{ Id = "com.txa.home";       Out = "app\src\main\assets\txahome.apk" },
    @{ Id = "com.gathin.tvhome";  Out = "app\src\main\assets\tvhome.apk" },
    @{ Id = "com.ts.launcher";    Out = "app\src\main\assets\tvhome-xiaomi.apk" }
)

foreach ($v in $variants) {
    .\gradlew.bat :launcher:assembleRelease "-PlauncherApplicationId=$($v.Id)" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build launcher ($($v.Id)) thất bại" }
    Copy-Item launcher\build\outputs\apk\release\launcher-release.apk $v.Out -Force
}

Get-Item app\src\main\assets\txahome.apk, app\src\main\assets\tvhome.apk, app\src\main\assets\tvhome-xiaomi.apk | Select-Object Name, Length
Write-Host "Xong. Nhớ build lại app (:app:assembleDebug) để APK launcher mới nằm trong APK."
