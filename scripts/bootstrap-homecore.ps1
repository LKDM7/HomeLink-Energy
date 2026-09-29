param([string]$HomeCorePath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($HomeCorePath)) { $HomeCorePath = Join-Path $projectRoot '../HomeCore' }
$propertiesPath = Join-Path $HomeCorePath 'gradle.properties'
if (!(Test-Path -LiteralPath $propertiesPath -PathType Leaf) -or !(Test-Path -LiteralPath (Join-Path $HomeCorePath 'settings.gradle') -PathType Leaf)) {
    throw 'Checkout HomeCore absent. Cloner la version voulue dans ../HomeCore ou passer -HomeCorePath <chemin>.'
}
function Read-Property([string]$Path, [string]$Key) {
    $line = Get-Content -LiteralPath $Path | Where-Object { $_ -match ('^' + [regex]::Escape($Key) + '=') } | Select-Object -First 1
    if (!$line) { throw ('Propriete absente : ' + $Key) }
    return $line.Substring($line.IndexOf('=') + 1).Trim()
}
$expected = Read-Property (Join-Path $projectRoot 'gradle.properties') 'homecore_version'
$actual = Read-Property $propertiesPath 'mod_version'
$minecraft = Read-Property $propertiesPath 'minecraft_version'
if ($actual -ne $expected -or $minecraft -ne '1.21.1') { throw ('HomeCore attendu : ' + $expected + ', Minecraft 1.21.1 ; trouve : ' + $actual + ', ' + $minecraft) }
Write-Output ('HomeCore ' + $actual + ' verifie : ' + (Resolve-Path -LiteralPath $HomeCorePath).Path)
Write-Output 'Aucun fichier modifie. Utiliser -Phomecore_dir=<chemin> si ce checkout ne se trouve pas dans ../HomeCore.'
