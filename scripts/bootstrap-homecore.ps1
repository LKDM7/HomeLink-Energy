$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$dependencyPath = Join-Path $projectRoot '.dependencies/HomeCore'
$commit = '5ae6db2864809d53308126a6d581c7944f9a7c64'
if (Test-Path -LiteralPath $dependencyPath) {
    $head = git -C $dependencyPath rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -ne $commit) { throw 'Existing dependency differs; use a clean checkout at the pinned commit.' }
    $dirty = git -C $dependencyPath status --porcelain --untracked-files=all
    if ($dirty) { throw 'Existing dependency contains local changes; left untouched.' }
} else {
    git clone https://github.com/LKDM7/HomeCore.git $dependencyPath
    if ($LASTEXITCODE -ne 0) { throw 'HomeCore clone failed. Authenticate to GitHub, or provide a verifiable local checkout.' }
    git -C $dependencyPath checkout --detach $commit
    if ($LASTEXITCODE -ne 0) { throw 'Pinned HomeCore commit unavailable.' }
}
Write-Output "HomeCore 1.9.0 pinned at $commit"
