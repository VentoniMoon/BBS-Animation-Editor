<#
.SYNOPSIS
  Stage an isolated full-modpack and heavy-world stress test for BBS Animation Editor.
.DESCRIPTION
  Copies local mod JARs, complete world folder, and optional game assets into
  run-stress/. Does NOT modify run/, original modpack, or original world.
.EXAMPLE
  .\tools\prepare-stress-test.ps1 -ModsDirectory "D:\MyPack\mods" -WorldDirectory "D:\MyPack\saves\BigWorld"
.EXAMPLE
  .\tools\prepare-stress-test.ps1 -ModsDirectory "D:\MyPack\mods" -WorldDirectory "E:\MyWorld" -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModsDirectory,
    [Parameter(Mandatory = $true)]
    [string] $WorldDirectory,
    [string] $PackRoot,
    [switch] $SkipExtraAssets
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-Directory([string] $Path, [string] $Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Description is not a directory: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).ProviderPath.TrimEnd('\')
}

function Copy-Directory([string] $Source, [string] $Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    & robocopy.exe $Source $Destination /E /XJ /R:1 /W:1 /NP /NJH /NJS /NFL /NDL | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "Copy failed (robocopy exit code $LASTEXITCODE): $Source"
    }
}

$project = Resolve-Directory (Join-Path $PSScriptRoot '..') 'Editor project'
$mods = Resolve-Directory $ModsDirectory 'Modpack mods folder'
$world = Resolve-Directory $WorldDirectory 'World folder'
if (-not (Test-Path -LiteralPath (Join-Path $project 'build.gradle') -PathType Leaf)) {
    throw 'Not an editor project: build.gradle was not found.'
}
if (-not (Test-Path -LiteralPath (Join-Path $world 'level.dat') -PathType Leaf)) {
    throw 'WorldDirectory must point to the folder that contains level.dat (not to saves).'
}
if ([string]::IsNullOrWhiteSpace($PackRoot)) {
    $pack = Split-Path -Parent $mods
} else {
    $pack = Resolve-Directory $PackRoot 'Pack root'
}

$target = Join-Path $project 'run-stress'
if (Test-Path -LiteralPath $target) {
    throw "Refusing to overwrite $target. Rename or move the old profile first."
}

$jarFiles = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File -Recurse |
    Where-Object { $_.Name -notmatch '(?i)^bbs[-_. ]?animation[-_. ]?editor' })
if ($jarFiles.Count -lt 1) {
    throw 'No mod JARs found. Check ModsDirectory.'
}
$editorJars = @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File -Recurse |
    Where-Object { $_.Name -match '(?i)^bbs[-_. ]?animation[-_. ]?editor' })
if ($editorJars.Count -gt 0) {
    Write-Warning 'Existing BBS Animation Editor JAR will NOT be copied (runClient already loads editor classes).'
}

Write-Host "Editor:  $project"
Write-Host "Modpack: $mods ($($jarFiles.Count) JAR files)"
Write-Host "World:   $world"
Write-Host "Profile: $target"
Write-Host "Original run/ and the original world will not be modified."

$required = @('blockbuster-2.7.1', 'emoticons-1.1.2',
    'mclib-2.4.3', 'metamorph-1.4', 'aperture-1.8.2')
foreach ($prefix in $required) {
    if (@($jarFiles | Where-Object { $_.Name.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase) }).Count -eq 0) {
        Write-Warning "Expected editor dependency '$prefix' was not found."
    }
}
if (@($jarFiles | Where-Object { $_.Name -match '(?i)MorePlayerModels' }).Count -gt 0) {
    Write-Warning 'Metamorph + MorePlayerModels can conflict over POV. Check camera settings if it bobs.'
}
if (-not $PSCmdlet.ShouldProcess($target,
        'Create private stress profile; copy JARs, optional pack assets and complete world')) {
    return
}

New-Item -Path (Join-Path $target 'mods') -ItemType Directory -Force | Out-Null
foreach ($jar in $jarFiles) {
    $relative = $jar.FullName.Substring($mods.Length).TrimStart('\')
    $output = Join-Path (Join-Path $target 'mods') $relative
    $outputParent = Split-Path -Parent $output
    if (-not (Test-Path -LiteralPath $outputParent)) {
        New-Item -Path $outputParent -ItemType Directory -Force | Out-Null
    }
    Copy-Item -LiteralPath $jar.FullName -Destination $output
}
if (-not $SkipExtraAssets) {
    # Config/shaderpacks/resourcepacks are needed for accurate visual load.
    # 'saves' is intentionally excluded: copy only the specified world below.
    $folders = @('config', 'shaderpacks', 'resourcepacks', 'customnpcs',
        'schematics', 'scripts')
    foreach ($name in $folders) {
        $source = Join-Path $pack $name
        if (Test-Path -LiteralPath $source -PathType Container) {
            Write-Host "Copying optional assets: $name"
            Copy-Directory $source (Join-Path $target $name)
        }
    }
    foreach ($name in @('options.txt', 'optionsof.txt', 'optionsshaders.txt')) {
        $source = Join-Path $pack $name
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $target $name)
        }
    }
}

$worldName = Split-Path -Leaf $world
$worldTarget = Join-Path (Join-Path $target 'saves') $worldName
Write-Host "Copying complete world, including region files and level.dat..."
Copy-Directory $world $worldTarget

$manifest = @(
    '# BBS Animation Editor - local stress profile',
    "Created: $(Get-Date -Format o)",
    "JAR count: $($jarFiles.Count)",
    "World folder: $worldName",
    '',
    '# Mod JARs (filenames and sizes in bytes)',
    ($jarFiles | Sort-Object FullName | ForEach-Object {
        "$($_.Name)  $($_.Length)"
    })
)
$manifest | Set-Content -LiteralPath (Join-Path $target 'stress-manifest.txt') -Encoding UTF8

Write-Host ''
Write-Host 'Stress profile ready. Run from the editor project root:'
Write-Host '    .\gradlew.bat runClient -PstressTest'
Write-Host 'Open the copied world from Singleplayer. Use Java 8 and an appropriate'
Write-Host 'GAME JVM heap (for example -Xmx6G or -Xmx8G if your RAM allows).'
Write-Host 'org.gradle.jvmargs sets Gradle memory, not necessarily game memory.'
Write-Host 'Normal profile: .\gradlew.bat runClient'
