param(
	[string]$OutputName = 'FermataX-introduction-v300.mp4'
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $Root '..\..')).Path
$Assets = Join-Path $Root 'assets'
$Work = Join-Path $Root 'work'
$Dist = Join-Path $RepoRoot 'dist\promo'
$Output = Join-Path $Dist $OutputName
$FontRegular = 'C:\Windows\Fonts\segoeui.ttf'
$FontSemibold = 'C:\Windows\Fonts\seguisb.ttf'
$FontRegularFilter = $FontRegular.Replace('\', '/').Replace(':', '\:')
$FontSemiboldFilter = $FontSemibold.Replace('\', '/').Replace(':', '\:')

function Assert-File([string]$Path) {
	if (-not (Test-Path -LiteralPath $Path)) {
		throw "Required file is missing: $Path"
	}
}

function Invoke-Ffmpeg([string[]]$Arguments) {
	& ffmpeg -hide_banner -loglevel warning -y @Arguments
	if ($LASTEXITCODE -ne 0) {
		throw "ffmpeg failed with exit code $LASTEXITCODE"
	}
}

function Caption-Filter([string]$Caption) {
	if ([string]::IsNullOrWhiteSpace($Caption)) {
		return ''
	}

	return ",drawbox=x=0:y=980:w=1920:h=100:color=0x05080D@0.90:t=fill" +
		",drawtext=fontfile='$FontSemiboldFilter':text='$Caption':fontcolor=white:fontsize=32:x=(w-tw)/2:y=1004"
}

function Build-VideoSegment(
	[string]$InputFile,
	[string]$OutputFile,
	[double]$Start,
	[double]$Duration,
	[string]$Caption,
	[string]$PreFilter = ''
) {
	$fadeOut = $Duration - 0.30
	$filter = $PreFilter +
		'scale=1920:1080:force_original_aspect_ratio=decrease,' +
		'pad=1920:1080:(ow-iw)/2:(oh-ih)/2:0x080D14' +
		(Caption-Filter $Caption) +
		",fade=t=in:st=0:d=0.30,fade=t=out:st=$fadeOut`:d=0.30"

	Invoke-Ffmpeg @(
		'-ss', "$Start",
		'-i', $InputFile,
		'-t', "$Duration",
		'-vf', $filter,
		'-an',
		'-r', '30',
		'-c:v', 'libx264',
		'-preset', 'medium',
		'-crf', '18',
		'-pix_fmt', 'yuv420p',
		'-g', '60',
		'-keyint_min', '60',
		'-sc_threshold', '0',
		$OutputFile
	)
}

function Build-ImageSegment(
	[string]$InputFile,
	[string]$OutputFile,
	[double]$Duration,
	[string]$Caption,
	[string]$PreFilter = ''
) {
	$fadeOut = $Duration - 0.30
	$filter = $PreFilter +
		"scale=1960:1120:force_original_aspect_ratio=increase," +
		"crop=1920:1080:x='20+12*sin(t/3)':y='20+8*sin(t/4)'" +
		(Caption-Filter $Caption) +
		",fade=t=in:st=0:d=0.30,fade=t=out:st=$fadeOut`:d=0.30"

	Invoke-Ffmpeg @(
		'-loop', '1',
		'-framerate', '30',
		'-i', $InputFile,
		'-t', "$Duration",
		'-vf', $filter,
		'-an',
		'-r', '30',
		'-c:v', 'libx264',
		'-preset', 'medium',
		'-crf', '18',
		'-pix_fmt', 'yuv420p',
		'-g', '60',
		'-keyint_min', '60',
		'-sc_threshold', '0',
		$OutputFile
	)
}

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
	throw 'ffmpeg is not available.'
}

$required = @(
	'dashboard-top.png',
	'dashboard-addons.png',
	'tv-channels.png',
	'tv-splitview.png',
	'scenic-motion-dhu.mp4',
	'showcase.mp4',
	'feature-highlights.mp4',
	'fermatax-logo.png'
)
$required | ForEach-Object { Assert-File (Join-Path $Assets $_) }
Assert-File $FontRegular
Assert-File $FontSemibold

New-Item -ItemType Directory -Force -Path $Work, $Dist | Out-Null

$segments = 1..9 | ForEach-Object { Join-Path $Work ("segment-{0:D2}.mp4" -f $_) }

Build-VideoSegment (Join-Path $Assets 'showcase.mp4') $segments[0] 0 6 ''

$cleanRecent =
	"drawbox=x=520:y=62:w=270:h=132:color=0x142033@1:t=fill," +
	"drawtext=fontfile='$FontSemiboldFilter':text='Recent':fontcolor=white:fontsize=15:x=536:y=80," +
	"drawtext=fontfile='$FontRegularFilter':text='FermataX Showcase':fontcolor=0xD7DEEA:fontsize=14:x=536:y=108," +
	"drawtext=fontfile='$FontRegularFilter':text='Scenic Drive':fontcolor=0xD7DEEA:fontsize=14:x=536:y=135," +
	"drawtext=fontfile='$FontRegularFilter':text='Feature Highlights':fontcolor=0xD7DEEA:fontsize=14:x=536:y=162,"

Build-ImageSegment (Join-Path $Assets 'dashboard-top.png') $segments[1] 7 'ONE DASHBOARD. ALL YOUR MEDIA.' $cleanRecent
Build-ImageSegment (Join-Path $Assets 'dashboard-addons.png') $segments[2] 6 'TV, RADIO, PODCASTS, AUDIOBOOKS AND MORE.'
Build-ImageSegment (Join-Path $Assets 'tv-channels.png') $segments[3] 6 'BRING YOUR OWN AUTHORIZED TV SOURCES.'
Build-VideoSegment (Join-Path $Assets 'scenic-motion-dhu.mp4') $segments[4] 0 7 'FULLSCREEN PLAYBACK BUILT FOR ANDROID AUTO.'
Build-ImageSegment (Join-Path $Assets 'tv-splitview.png') $segments[5] 7 'SWITCH CHANNELS WITHOUT LOSING CONTEXT.'
Build-VideoSegment (Join-Path $Assets 'feature-highlights.mp4') $segments[6] 12 7 'SIMPLE CONTROLS. PREDICTABLE NAVIGATION.'
Build-VideoSegment (Join-Path $Assets 'feature-highlights.mp4') $segments[7] 24 7 'IN-APP VOICE FOR COMPATIBLE MEDIA.'

$outroFilter = @"
[1:v]scale=250:250[logo];
[0:v][logo]overlay=(W-w)/2:116,
drawtext=fontfile='$FontSemiboldFilter':text='FermataX':fontcolor=white:fontsize=72:x=(w-tw)/2:y=404,
drawtext=fontfile='$FontRegularFilter':text='Media made for the road':fontcolor=0xC8D3E5:fontsize=34:x=(w-tw)/2:y=500,
drawtext=fontfile='$FontRegularFilter':text='An independent Fermata fork':fontcolor=0x78A7FF:fontsize=26:x=(w-tw)/2:y=568,
drawtext=fontfile='$FontSemiboldFilter':text='github.com/chuoinho/FermataX':fontcolor=white:fontsize=30:x=(w-tw)/2:y=684,
drawtext=fontfile='$FontRegularFilter':text='ko-fi.com/fermatax':fontcolor=0xAFC7EE:fontsize=26:x=(w-tw)/2:y=736,
drawtext=fontfile='$FontRegularFilter':text='FermataX does not provide or host media content. Use responsibly and follow local driving laws.':fontcolor=0x778399:fontsize=18:x=(w-tw)/2:y=1015,
fade=t=in:st=0:d=0.40,fade=t=out:st=6.50:d=0.50[v]
"@ -replace "`r|`n", ''

Invoke-Ffmpeg @(
	'-f', 'lavfi', '-i', 'color=c=0x080D14:s=1920x1080:r=30:d=7',
	'-loop', '1', '-i', (Join-Path $Assets 'fermatax-logo.png'),
	'-filter_complex', $outroFilter,
	'-map', '[v]',
	'-t', '7',
	'-an',
	'-r', '30',
	'-c:v', 'libx264',
	'-preset', 'medium',
	'-crf', '18',
	'-pix_fmt', 'yuv420p',
	'-g', '60',
	'-keyint_min', '60',
	'-sc_threshold', '0',
	$segments[8]
)

$concatFile = Join-Path $Work 'segments.txt'
$concatLines = $segments | ForEach-Object { "file '$($_.Replace("'", "''"))'" }
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($concatFile, $concatLines, $utf8NoBom)

$videoOnly = Join-Path $Work 'video-only.mp4'
Invoke-Ffmpeg @('-f', 'concat', '-safe', '0', '-i', $concatFile, '-c', 'copy', $videoOnly)

$ambient = 'aevalsrc=0.006*(sin(2*PI*55*t)+0.58*sin(2*PI*82.41*t)+0.32*sin(2*PI*110*t))*(0.72+0.28*sin(2*PI*0.05*t))|0.006*(sin(2*PI*55*t)+0.58*sin(2*PI*82.41*t)+0.32*sin(2*PI*110*t))*(0.72+0.28*sin(2*PI*0.05*t)):s=48000:d=60'
Invoke-Ffmpeg @(
	'-i', $videoOnly,
	'-f', 'lavfi', '-i', $ambient,
	'-filter:a', 'lowpass=f=1200,volume=6,afade=t=in:st=0:d=2,afade=t=out:st=57:d=3',
	'-map', '0:v:0',
	'-map', '1:a:0',
	'-c:v', 'copy',
	'-c:a', 'aac',
	'-b:a', '128k',
	'-movflags', '+faststart',
	'-shortest',
	$Output
)

Write-Output "Promo video created: $Output"
