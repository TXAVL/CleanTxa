# Dựng lại CleanTxa Home (module :launcher, applicationId com.txa.home) và đưa vào assets của app.
# Chạy từ thư mục gốc dự án:  powershell -File tools\build-txahome.ps1
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
.\gradlew.bat :launcher:assembleRelease "-PlauncherApplicationId=com.txa.home" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build launcher thất bại" }
Copy-Item launcher\build\outputs\apk\release\launcher-release.apk app\src\main\assets\txahome.apk -Force
Get-Item app\src\main\assets\txahome.apk | Select-Object Name, Length
Write-Host "Xong. Nhớ build lại app (:app:assembleDebug) để txahome.apk mới nằm trong APK."
