# Renders every phone in the mockup to its own PNG.
#
# Headless Edge screenshots a window, not an element, so one screen has to be isolated
# per render. An earlier iframe-based version raced: Edge captured the page before the
# iframe's load handler had hidden the other screens, and most shots came out as the
# whole scrolling page. The isolating CSS is therefore injected into a *copy* of the
# mockup, inline and at the end of <body>, where it is applied during parsing and cannot
# lose a race with the screenshot.
#
# index.html itself is never modified: the copy is regenerated from it on every run, so
# what gets photographed is always the current mockup.
#
# Rendered at device-scale-factor 2 - the frame is 316 px wide, below the 320 px minimum
# Play requires of a screenshot, and 2x clears it comfortably.

$ErrorActionPreference = 'Stop'

$edge = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $edge) { throw 'Microsoft Edge not found; needed to render the mockup.' }

$here = $PSScriptRoot
$source = Get-Content (Join-Path $here 'index.html') -Raw

# Caption text drives the file names, so renaming a screen in the mockup renames its PNG.
# Only screens that actually contain a phone frame are rendered: the mockup also holds
# design-decision panels (layout comparisons, icon candidates) which are wider than a
# handset and would come out cropped and meaningless.
$blocks = [regex]::Matches($source, '(?s)<div class="screen">(.*?)(?=<div class="screen">|</div>\s*<!--\s*rail end|\z)')

$captions = @()
$indices = @()
for ($b = 0; $b -lt $blocks.Count; $b++) {
    $body = $blocks[$b].Value
    if ($body -notmatch 'class="phone"') { continue }
    $cap = [regex]::Match($body, '<div class="cap">(.*?)</div>')
    if (-not $cap.Success) { continue }
    $captions += $cap.Groups[1].Value
    $indices += ($b + 1)
}

if ($captions.Count -eq 0) { throw 'No phone screens found in the mockup.' }

Get-ChildItem $here -Filter 'screen-*.png' -ErrorAction SilentlyContinue | Remove-Item

$isolate = @'
<style id="isolate">
  body { padding: 0 !important; margin: 0 !important; background: #0b0c10 !important; }
  h1, .sub, .note, .legend { display: none !important; }
  .rail { display: block !important; overflow: visible !important; padding: 0 !important; gap: 0 !important; }
  .screen { display: none !important; }
  .screen.render-me { display: block !important; }
  .screen.render-me .cap { display: none !important; }
  .phone { margin: 0 !important; }
</style>
<script>
  (function () {
    var n = parseInt(new URLSearchParams(location.search).get('n') || '1', 10);
    var target = document.querySelectorAll('.screen')[n - 1];
    if (target) { target.classList.add('render-me'); }
  })();
</script>
'@

$shot = Join-Path $here 'shot.html'
($source -replace '(?i)</body>', "$isolate`n</body>") |
    Set-Content $shot -Encoding utf8

$rendered = @()
for ($k = 0; $k -lt $captions.Count; $k++) {
    $i = $indices[$k]
    $label = $captions[$k] `
        -replace '<[^>]+>', '' `
        -replace '&middot;|&mdash;|&rarr;|&amp;', ' ' `
        -replace '[^A-Za-z0-9]+', '-' `
        -replace '(^-+|-+$)', ''
    $name = 'screen-{0:d2}-{1}.png' -f ($k + 1), $label.ToLower()
    $target = Join-Path $here $name

    # Edge chatters on stderr even on success; with 'Stop' that becomes a terminating
    # NativeCommandError. The real check is whether the file appeared.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 `
        --virtual-time-budget=2000 `
        --screenshot="$target" --window-size=316,636 `
        "file:///$($here.Replace('\','/'))/shot.html?n=$i" 2>&1 | Out-Null
    $ErrorActionPreference = $previous
    Start-Sleep -Seconds 2

    if (-not (Test-Path $target)) { throw "Failed to render screen $i" }
    $rendered += $name
}
$rendered
