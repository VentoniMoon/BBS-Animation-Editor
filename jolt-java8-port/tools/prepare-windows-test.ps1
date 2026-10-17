<#
.SYNOPSIS
  Install the pinned experimental Jolt Java 8 libraries into editor/libs.
.DESCRIPTION
  Reads the jolt-java8-experimental-jars.zip archive produced by the
  feature/jolt-java8-port GitHub Actions job. Verifies that the expected
  native and Java classes are in the archive before copying two jars.
  Leaves the main editor's default build unchanged.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File jolt-java8-port/tools/prepare-windows-test.ps1 -ArtifactZip "C:\Users\me\Downloads\jolt-java8-experimental-jars.zip" -Build
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ArtifactZip,
    [switch] $Build
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$artifactPath = (Resolve-Path $ArtifactZip).Path
$destination = Join-Path $repoRoot 'libs'
$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("bbs-jolt-" + [Guid]::NewGuid().ToString('N'))
$files = @(
    'jolt-jni-6.0.0-java8-bundled.jar',
    'jolt-hair-prototype-java8.jar'
)

function Assert-JarEntries([string] $jarPath, [string[]] $entries) {
    $jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $names = @{}
        foreach ($entry in $jar.Entries) { $names[$entry.FullName] = $true }
        foreach ($entryName in $entries) {
            if (!$names.ContainsKey($entryName)) {
                throw "The artifact is not the expected Jolt Java 8 build: $entryName is missing from $jarPath"
            }
        }
    } finally {
        $jar.Dispose()
    }
}

New-Item -ItemType Directory -Path $temporary -Force | Out-Null
try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($artifactPath, $temporary)
    foreach ($file in $files) {
        $source = Join-Path $temporary $file
        if (!(Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "GitHub Actions artifact is missing $file"
        }
    }

    Assert-JarEntries (Join-Path $temporary $files[0]) @(
        'com/github/stephengold/joltjni/Jolt.class',
        'com/github/stephengold/joltjni/Java8Cleaner.class',
        'windows/x86-64/com/github/stephengold/joltjni.dll'
    )
    Assert-JarEntries (Join-Path $temporary $files[1]) @(
        'com/example/examplemod/physics/JoltHairChain.class',
        'com/example/examplemod/physics/JoltChameleonPreviewRig.class',
        'com/example/examplemod/physics/JoltJava8Bootstrap.class'
    )

    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    foreach ($file in $files) {
        Copy-Item -LiteralPath (Join-Path $temporary $file) -Destination (Join-Path $destination $file) -Force
        Write-Host "Installed: libs/$file"
    }
} finally {
    if (Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}

if ($Build) {
    $gradlew = Join-Path $repoRoot 'gradlew.bat'
    if (!(Test-Path -LiteralPath $gradlew)) {
        throw "Missing editor Gradle wrapper: $gradlew"
    }
    Push-Location $repoRoot
    try {
        & $gradlew clean build -PenableJoltExperimental
        if ($LASTEXITCODE -ne 0) {
            throw "Experimental Forge build failed (Gradle exit code $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
}
Write-Host "Jolt Java 8 experiment prepared. Enable with -Dbbs.editor.physics.jolt.preview=true"
