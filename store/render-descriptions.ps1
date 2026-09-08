# Builds descriptions.html - the paste-ready copy of the two store descriptions.
#
# listing.md is the single source of truth; this script extracts the fenced blocks from
# it, so the page can never drift from the reviewed copy.
#
# The important step is unwrapping. listing.md is hard-wrapped at 88 columns so it can be
# read and diffed, but Play Console preserves every newline in the description field. A
# verbatim paste would show those wraps as real line breaks and look ragged on a narrow
# phone. Paragraph lines are therefore rejoined into single long lines; blank lines and
# bullet items are kept, because those breaks are meant.

$ErrorActionPreference = 'Stop'

$here = $PSScriptRoot
$md = Get-Content (Join-Path $here 'listing.md') -Raw

function Get-Block([string]$heading) {
    $pattern = '(?s)##\s+' + [regex]::Escape($heading) + '.*?```\r?\n(.*?)```'
    $m = [regex]::Match($md, $pattern)
    if (-not $m.Success) { throw "Could not find the '$heading' block in listing.md" }
    $m.Groups[1].Value.TrimEnd()
}

function Expand-Wrapping([string]$text) {
    $out = [System.Collections.Generic.List[string]]::new()
    $buffer = ''

    foreach ($line in ($text -split "\r?\n")) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '') {
            if ($buffer) { $out.Add($buffer); $buffer = '' }
            $out.Add('')
        }
        elseif ($trimmed.StartsWith('- ')) {
            if ($buffer) { $out.Add($buffer); $buffer = '' }
            $out.Add($trimmed)
        }
        else {
            $buffer = if ($buffer) { "$buffer $trimmed" } else { $trimmed }
        }
    }
    if ($buffer) { $out.Add($buffer) }

    # Collapse the runs of blank lines used as visual spacing in the source down to the
    # single blank line that actually separates paragraphs on the store page.
    ($out -join "`n") -replace "\n{3,}", "`n`n"
}

$short = (Get-Block 'Short description (80 characters max)').Trim()
$full = Expand-Wrapping (Get-Block 'Full description (4000 characters max)')

if ($short.Length -gt 80) { throw "Short description is $($short.Length) characters; Play allows 80." }
if ($full.Length -gt 4000) { throw "Full description is $($full.Length) characters; Play allows 4000." }

function Escape-Html([string]$s) {
    $s.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
}

$template = Get-Content (Join-Path $here 'descriptions.template.html') -Raw
$html = $template.
    Replace('{{SHORT}}', (Escape-Html $short)).
    Replace('{{FULL}}', (Escape-Html $full)).
    Replace('{{SHORT_LEN}}', $short.Length).
    Replace('{{FULL_LEN}}', $full.Length)

$target = Join-Path $here 'descriptions.html'
[System.IO.File]::WriteAllText($target, $html, (New-Object System.Text.UTF8Encoding($false)))

"descriptions.html written - short $($short.Length)/80, full $($full.Length)/4000"
