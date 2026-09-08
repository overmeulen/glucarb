# Regenerates the Play Store graphics from their HTML sources.
#
# Headless Edge is used rather than an image library because the icon is defined as
# vector path data in the app's own drawable, and rendering it through a browser means
# the store icon is produced from that exact same path data. Editing the PNGs by hand
# would let the two drift apart silently.

$ErrorActionPreference = 'Stop'

$edge = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $edge) { throw 'Microsoft Edge not found; needed to render the graphics.' }

$store = $PSScriptRoot

function Render($htmlName, $pngName, $width, $height) {
    $target = Join-Path $store $pngName
    if (Test-Path $target) { Remove-Item $target }
    & $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 `
        --screenshot="$target" --window-size="$width,$height" `
        ("file:///" + (Join-Path $store $htmlName).Replace('\', '/')) 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    if (-not (Test-Path $target)) { throw "Failed to render $pngName" }
}

Render 'icon.html' 'icon-512.png' 512 512
Render 'feature-graphic.html' 'feature-graphic-1024x500.png' 1024 500

# Play requires the listing icon to be a 32-bit PNG. Edge emits 24-bit, so add the
# alpha channel back.
Add-Type -AssemblyName System.Drawing
$iconPath = Join-Path $store 'icon-512.png'
$src = [System.Drawing.Image]::FromFile($iconPath)
$bmp = New-Object System.Drawing.Bitmap 512, 512, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.DrawImage($src, 0, 0, 512, 512)
$g.Dispose()
$src.Dispose()
$temp = Join-Path $store 'icon-512.tmp.png'
$bmp.Save($temp, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Move-Item $temp $iconPath -Force

Get-ChildItem $store -Filter *.png | ForEach-Object {
    $i = [System.Drawing.Image]::FromFile($_.FullName)
    "{0}: {1}x{2} {3}" -f $_.Name, $i.Width, $i.Height, $i.PixelFormat
    $i.Dispose()
}
