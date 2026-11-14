param(
    [Parameter(Mandatory=$true)][string]$BaseDirectory,
    [Parameter(Mandatory=$true)][string]$StageDirectory,
    [Parameter(Mandatory=$true)][string]$CrtDirectory,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$SourceCommit,
    [string]$QtDirectory = 'C:\Qt\6.10.0\msvc2022_64'
)
$ErrorActionPreference = 'Stop'
$base = (Resolve-Path -LiteralPath $BaseDirectory).Path
$stage = [IO.Path]::GetFullPath($StageDirectory)
if (Test-Path -LiteralPath $stage) { throw 'Use a new stage directory; existing files are preserved.' }
$executable = Join-Path $base 'win\build\bin\mytellphone.exe'
$sdk = Join-Path $base 'win\sdk\linphone-sdk\win64'
$source = Join-Path $base 'linphone-desktop'
$crt = (Resolve-Path -LiteralPath $CrtDirectory).Path
if ((Split-Path $crt -Leaf) -ne 'Microsoft.VC143.CRT' -or
    (Split-Path (Split-Path $crt -Parent) -Leaf) -ne 'x64' -or $crt -match '(?i)onecore') {
    throw 'Select the standard x64 Microsoft.VC143.CRT redistributable directory.'
}
$deployer = Join-Path $QtDirectory 'bin\windeployqt.exe'
foreach ($required in @($executable, $sdk, $source, $deployer,
    (Join-Path $sdk 'lib\mediastreamer\plugins\libmsqogl.dll'),
    (Join-Path $QtDirectory 'bin\Qt6OpenGL.dll'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item -LiteralPath $executable -Destination $stage
Set-Content -LiteralPath (Join-Path $stage 'SOURCE-COMMIT.txt') -Value $SourceCommit.ToLowerInvariant() -Encoding ascii
Get-ChildItem -LiteralPath $crt -Filter '*.dll' -File | Copy-Item -Destination $stage
$env:PATH = (Join-Path $QtDirectory 'bin') + ';' + $env:PATH
& $deployer --release --qmldir (Join-Path $source 'Linphone\view') (Join-Path $stage 'mytellphone.exe')
if ($LASTEXITCODE -ne 0) { throw 'Qt runtime deployment failed' }
Get-ChildItem -LiteralPath (Join-Path $sdk 'bin') -Filter '*.dll' -File | Copy-Item -Destination $stage
foreach ($relative in @('lib\mediastreamer\plugins','lib\linphone\plugins','share\sounds','share\belr','share\images')) {
    $from = Join-Path $sdk $relative
    if (Test-Path -LiteralPath $from) {
        $destination = Join-Path $stage $relative
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        if ($relative.StartsWith('lib\')) {
            Get-ChildItem -LiteralPath $from -Filter '*.dll' -File | Copy-Item -Destination $destination
        } else {
            Get-ChildItem -LiteralPath $from -Force | Copy-Item -Destination $destination -Recurse
        }
    }
}
$factoryDirectory = Join-Path $stage 'share\mytellphone'
New-Item -ItemType Directory -Path $factoryDirectory -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'Linphone\data\config\linphonerc-factory') -Destination $factoryDirectory
# Paths.cpp expects share/linphone/rootca.pem, including in a branded build.
$caDirectory = Join-Path $stage 'share\linphone'
New-Item -ItemType Directory -Path $caDirectory -Force | Out-Null
$caCandidates = @((Join-Path $sdk 'share\linphone\rootca.pem'), (Join-Path $sdk 'share\rootca.pem'))
$ca = $caCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $ca) { throw 'Missing SDK trust root bundle' }
Copy-Item -LiteralPath $ca -Destination (Join-Path $caDirectory 'rootca.pem')
Copy-Item -LiteralPath (Join-Path $source 'LICENSE.txt') -Destination (Join-Path $stage 'LICENSE-GPL-3.0.txt')
Copy-Item -LiteralPath (Join-Path $base 'linphone-sdk\LICENSE.txt') -Destination (Join-Path $stage 'LICENSE-SDK-AGPL-3.0.txt')
$noticeDirectory = $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $noticeDirectory 'THIRD-PARTY-NOTICES.md'))) {
    $noticeDirectory = Join-Path $base 'linphone-android\release\windows'
}
foreach ($notice in @('THIRD-PARTY-NOTICES.md', 'LICENSE-LGPL-3.0.txt', 'MICROSOFT-VC-RUNTIME-NOTICE.txt')) {
    Copy-Item -LiteralPath (Join-Path $noticeDirectory $notice) -Destination $stage
}
$qtSbom = Join-Path $QtDirectory 'sbom'
if (Test-Path -LiteralPath $qtSbom) {
    $sbomStage = Join-Path $stage 'qt-sbom'
    New-Item -ItemType Directory -Path $sbomStage | Out-Null
    Get-ChildItem -LiteralPath $qtSbom -Filter '*.spdx.json' -File | Copy-Item -Destination $sbomStage
}
$redistributable = Join-Path $base 'win\dist\MytellPhone\vc_redist.x64.exe'
if (Test-Path -LiteralPath $redistributable) { Copy-Item -LiteralPath $redistributable -Destination $stage }
Write-Output "Staged Windows runtime: $stage"
