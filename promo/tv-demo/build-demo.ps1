param(
	[int]$Port = 8765,
	[int]$Duration = 36
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $Root '..\..')).Path
$Assets = Join-Path $Root 'assets'
$Output = Join-Path $Root 'output'
$OutputAssets = Join-Path $Output 'assets'
$Channels = Join-Path $Output 'channels'
$FontRegular = 'C:\Windows\Fonts\segoeui.ttf'
$FontSemibold = 'C:\Windows\Fonts\seguisb.ttf'
$Logo = Join-Path $Assets 'fermatax-logo.png'
$Road = Join-Path $Assets 'scenic-road.png'

function Assert-Command([string]$Name) {
	if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
		throw "Required command is not available: $Name"
	}
}

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

function Convert-ToHls([string]$InputFile, [string]$ChannelName) {
	$ChannelDir = Join-Path $Channels $ChannelName
	New-Item -ItemType Directory -Force -Path $ChannelDir | Out-Null
	Invoke-Ffmpeg @(
		'-i', $InputFile,
		'-c', 'copy',
		'-hls_time', '2',
		'-hls_playlist_type', 'vod',
		'-hls_flags', 'independent_segments',
		'-hls_segment_filename', (Join-Path $ChannelDir 'segment-%03d.ts'),
		(Join-Path $ChannelDir 'index.m3u8')
	)
}

Assert-Command 'ffmpeg'
Assert-File $Logo
Assert-File $Road
Assert-File $FontRegular
Assert-File $FontSemibold

$FontRegularFilter = $FontRegular.Replace('\', '/').Replace(':', '\:')
$FontSemiboldFilter = $FontSemibold.Replace('\', '/').Replace(':', '\:')

New-Item -ItemType Directory -Force -Path $Assets, $Output, $OutputAssets, $Channels | Out-Null
Copy-Item -LiteralPath $Logo -Destination (Join-Path $OutputAssets 'fermatax-logo.png') -Force
Copy-Item -LiteralPath $Road -Destination (Join-Path $OutputAssets 'scenic-road.png') -Force
Invoke-Ffmpeg @(
	'-i', $Road,
	'-vf', 'scale=512:512:force_original_aspect_ratio=increase,crop=512:512',
	'-frames:v', '1',
	'-update', '1',
	'-q:v', '2',
	(Join-Path $OutputAssets 'scenic-channel.jpg')
)

$showcaseMp4 = Join-Path $Output 'showcase.mp4'
$scenicMp4 = Join-Path $Output 'scenic-drive.mp4'
$featuresMp4 = Join-Path $Output 'feature-highlights.mp4'

$commonVideo = @(
	'-t', "$Duration",
	'-r', '30',
	'-c:v', 'libx264',
	'-profile:v', 'high',
	'-level', '4.0',
	'-pix_fmt', 'yuv420p',
	'-preset', 'medium',
	'-crf', '20',
	'-g', '60',
	'-keyint_min', '60',
	'-sc_threshold', '0',
	'-c:a', 'aac',
	'-b:a', '96k',
	'-ar', '48000',
	'-ac', '2',
	'-movflags', '+faststart',
	'-shortest'
)

$showcaseFilter = @"
[1:v]scale=250:250[logo];
[0:v][logo]overlay=116:188,
drawbox=x=410:y=158:w=6:h=238:color=0x78A7FF@1:t=fill,
drawtext=fontfile='$FontSemiboldFilter':text='FermataX':fontcolor=white:fontsize=72:x=454:y=166,
drawtext=fontfile='$FontRegularFilter':text='Your media hub for Android Auto':fontcolor=0xC8D3E5:fontsize=32:x=458:y=258,
drawtext=fontfile='$FontSemiboldFilter':text='ONE DASHBOARD. ALL YOUR MEDIA.':fontcolor=0x78A7FF:fontsize=26:x=458:y=336:enable='between(t,0,12)',
drawtext=fontfile='$FontSemiboldFilter':text='TV  |  RADIO  |  PODCASTS  |  AUDIOBOOKS':fontcolor=0x78A7FF:fontsize=24:x=458:y=336:enable='between(t,12,24)',
drawtext=fontfile='$FontSemiboldFilter':text='BUILT FOR SIMPLE, FOCUSED CONTROL':fontcolor=0x78A7FF:fontsize=24:x=458:y=336:enable='gte(t,24)',
drawtext=fontfile='$FontRegularFilter':text='DEMO CHANNEL':fontcolor=0x778399:fontsize=20:x=w-tw-52:y=h-th-38[v]
"@ -replace "`r|`n", ''

$showcaseArgs = @(
	'-f', 'lavfi', '-i', "color=c=0x0B1018:s=1280x720:r=30:d=$Duration",
	'-loop', '1', '-i', $Logo,
	'-f', 'lavfi', '-i', 'anullsrc=channel_layout=stereo:sample_rate=48000',
	'-filter_complex', $showcaseFilter,
	'-map', '[v]', '-map', '2:a'
) + $commonVideo + @($showcaseMp4)
Invoke-Ffmpeg $showcaseArgs

$scenicFilter = @"
[0:v]scale=1380:776,crop=1280:720:x='50+20*sin(t/7)':y='28+8*sin(t/9)'[road];
[1:v]scale=88:88[logo];
[road][logo]overlay=48:48,
drawbox=x=46:y=ih-154:w=520:h=100:color=0x071019@0.76:t=fill,
drawtext=fontfile='$FontSemiboldFilter':text='SCENIC DRIVE':fontcolor=white:fontsize=38:x=72:y=h-137,
drawtext=fontfile='$FontRegularFilter':text='FermataX Demo TV':fontcolor=0xAFC7EE:fontsize=24:x=74:y=h-88[v]
"@ -replace "`r|`n", ''

$scenicArgs = @(
	'-loop', '1', '-i', $Road,
	'-loop', '1', '-i', $Logo,
	'-f', 'lavfi', '-i', 'anullsrc=channel_layout=stereo:sample_rate=48000',
	'-filter_complex', $scenicFilter,
	'-map', '[v]', '-map', '2:a'
) + $commonVideo + @($scenicMp4)
Invoke-Ffmpeg $scenicArgs

$featureFilter = @"
[1:v]scale=116:116[logo];
[0:v][logo]overlay=64:54,
drawtext=fontfile='$FontSemiboldFilter':text='FermataX':fontcolor=white:fontsize=34:x=202:y=73,
drawbox=x=64:y=202:w=1152:h=382:color=0x101824@1:t=fill,
drawtext=fontfile='$FontSemiboldFilter':text='SMART DASHBOARD':fontcolor=0x78A7FF:fontsize=30:x=(w-tw)/2:y=260:enable='between(t,0,12)',
drawtext=fontfile='$FontRegularFilter':text='Continue where you left off':fontcolor=white:fontsize=46:x=(w-tw)/2:y=330:enable='between(t,0,12)',
drawtext=fontfile='$FontSemiboldFilter':text='AA-FIRST NAVIGATION':fontcolor=0x78A7FF:fontsize=30:x=(w-tw)/2:y=260:enable='between(t,12,24)',
drawtext=fontfile='$FontRegularFilter':text='Simple controls. Predictable back.':fontcolor=white:fontsize=44:x=(w-tw)/2:y=330:enable='between(t,12,24)',
drawtext=fontfile='$FontSemiboldFilter':text='IN-APP VOICE':fontcolor=0x78A7FF:fontsize=30:x=(w-tw)/2:y=260:enable='gte(t,24)',
drawtext=fontfile='$FontRegularFilter':text='Find and control compatible media':fontcolor=white:fontsize=42:x=(w-tw)/2:y=330:enable='gte(t,24)',
drawtext=fontfile='$FontRegularFilter':text='ORIGINAL FERMATAX DEMO CONTENT':fontcolor=0x778399:fontsize=18:x=(w-tw)/2:y=530[v]
"@ -replace "`r|`n", ''

$featureArgs = @(
	'-f', 'lavfi', '-i', "color=c=0x080D14:s=1280x720:r=30:d=$Duration",
	'-loop', '1', '-i', $Logo,
	'-f', 'lavfi', '-i', 'anullsrc=channel_layout=stereo:sample_rate=48000',
	'-filter_complex', $featureFilter,
	'-map', '[v]', '-map', '2:a'
) + $commonVideo + @($featuresMp4)
Invoke-Ffmpeg $featureArgs

Convert-ToHls $showcaseMp4 'showcase'
Convert-ToHls $scenicMp4 'scenic-drive'
Convert-ToHls $featuresMp4 'feature-highlights'

$playlist = @"
#EXTM3U
#PLAYLIST:FermataX Demo TV
#EXTIMG:http://127.0.0.1:$Port/assets/fermatax-logo.png
#EXTINF:-1 tvg-id="fermatax.showcase" tvg-name="FermataX Showcase" tvg-logo="http://127.0.0.1:$Port/assets/fermatax-logo.png" group-title="FermataX Demo",FermataX Showcase
http://127.0.0.1:$Port/channels/showcase/index.m3u8
#EXTINF:-1 tvg-id="fermatax.scenic" tvg-name="Scenic Drive" tvg-logo="http://127.0.0.1:$Port/assets/scenic-channel.jpg" group-title="FermataX Demo",Scenic Drive
http://127.0.0.1:$Port/channels/scenic-drive/index.m3u8
#EXTINF:-1 tvg-id="fermatax.features" tvg-name="Feature Highlights" tvg-logo="http://127.0.0.1:$Port/assets/fermatax-logo.png" group-title="FermataX Demo",Feature Highlights
http://127.0.0.1:$Port/channels/feature-highlights/index.m3u8
"@

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $Output 'fermatax-demo.m3u'), $playlist, $utf8NoBom)
Write-Output "Demo TV package built in: $Output"
Write-Output "Playlist URL after starting the server: http://127.0.0.1:$Port/fermatax-demo.m3u"
