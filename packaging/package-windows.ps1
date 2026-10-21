<#
.SYNOPSIS
    Builds a Windows installer for Vektora that carries its own Java runtime.
.DESCRIPTION
    Packages the application jar and its dependencies with jpackage (part of the JDK, 17+).
    "exe" and "msi" need the WiX Toolset 3 on PATH; "app-image" needs nothing else and gives a
    folder with Vektora.exe that can be zipped.
    The bundled runtime is the JDK running this script; ONNX Runtime is pinned in pom.xml to the
    last version that loads with the MSVC runtime JDKs ship.
.EXAMPLE
    ./packaging/package-windows.ps1 -Type app-image
#>
param(
    [ValidateSet('exe', 'msi', 'app-image')]
    [string] $Type = 'exe',
    [string] $Version = '1.0.0'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    mvn -B -q -DskipTests package dependency:copy-dependencies "-DoutputDirectory=target/package-input" "-DincludeScope=runtime"
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    $jar = Get-ChildItem target -Filter '*.jar' | Where-Object { $_.Name -notlike '*-sources.jar' } | Select-Object -First 1
    Copy-Item $jar.FullName target/package-input/vectora.jar -Force
    Remove-Item target/installer -Recurse -Force -ErrorAction SilentlyContinue

    $arguments = @(
        '--type', $Type,
        '--name', 'Vectora',
        '--app-version', $Version,
        '--vendor', 'Vectora',
        '--description', 'PNG ve JPG gorsellerden SVG logo ve ikon paketi',
        '--input', 'target/package-input',
        '--main-jar', 'vectora.jar',
        '--main-class', 'org.example.Main',
        '--java-options', '-Xmx2g',
        '--dest', 'target/installer'
    )
    if ($Type -ne 'app-image') {
        $arguments += @('--win-menu', '--win-shortcut', '--win-dir-chooser', '--win-per-user-install')
    }
    jpackage @arguments
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
    Write-Host "Package written to $root\target\installer"
} finally {
    Pop-Location
}
