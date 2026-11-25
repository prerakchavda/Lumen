# One-time setup of Lumen's release signing key.
#
#  1. Creates the release keystore at %USERPROFILE%\.lumen\lumen-release.jks (outside the repo).
#  2. Writes the gitignored keystore.properties so builds on this PC use that key.
#  3. Installs the GitHub CLI if needed, signs you in, and stores the GitHub Actions secrets
#     (SEERFLIX_KEYSTORE_BASE64 / _PASSWORD / SEERFLIX_KEY_ALIAS / _KEY_PASSWORD, plus
#     TRAKT_CLIENT_ID when local.properties has trakt.clientId).
#
# The password is only typed here; it is never printed. Back up the .jks file and the password:
# without them no future Lumen build can update an installed copy.
#
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File scripts\setup-release-signing.ps1

$ErrorActionPreference = 'Stop'
$repo = 'prerakchavda/Lumen'
$alias = 'lumen'
$root = Split-Path -Parent $PSScriptRoot
$keyDir = Join-Path $env:USERPROFILE '.lumen'
$jks = Join-Path $keyDir 'lumen-release.jks'
$keytool = 'C:\Users\Prerak\.jdks\jdk-17.0.21+9\bin\keytool.exe'
if (-not (Test-Path $keytool)) { $keytool = 'keytool' }

function Read-Plain([string]$prompt) {
    $s = Read-Host -AsSecureString $prompt
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
}

# --- 1. Keystore -----------------------------------------------------------------------------
if (Test-Path $jks) {
    Write-Host "Keystore already exists at $jks - reusing it (it is never overwritten)."
    $pw = Read-Plain 'Password for the existing keystore'
} else {
    do {
        $pw = Read-Plain 'Choose a keystore password (8+ characters)'
        $pw2 = Read-Plain 'Type it again'
        if ($pw -ne $pw2) { Write-Host 'They did not match, try again.' }
        elseif ($pw.Length -lt 8) { Write-Host 'Too short, use at least 8 characters.' }
    } until ($pw -eq $pw2 -and $pw.Length -ge 8)
    New-Item -ItemType Directory -Force $keyDir | Out-Null
    $env:LUMEN_KS_PW = $pw
    try {
        & $keytool -genkeypair -keystore $jks -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 `
            -validity 36500 -dname 'CN=Lumen' -storepass:env LUMEN_KS_PW -keypass:env LUMEN_KS_PW
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
    } finally { Remove-Item Env:\LUMEN_KS_PW -ErrorAction SilentlyContinue }
    Write-Host "Created $jks"
}

# Check the password actually opens the keystore before storing it anywhere.
$env:LUMEN_KS_PW = $pw
try {
    & $keytool -list -keystore $jks -storepass:env LUMEN_KS_PW -alias $alias *> $null
    if ($LASTEXITCODE -ne 0) { throw 'That password does not open the keystore.' }
} finally { Remove-Item Env:\LUMEN_KS_PW -ErrorAction SilentlyContinue }

# --- 2. keystore.properties (gitignored) -----------------------------------------------------
$esc = { param($v) $v.Replace('\', '\\') }
$props = @(
    "storeFile=$($jks.Replace('\', '/'))"
    "storePassword=$(& $esc $pw)"
    "keyAlias=$alias"
    "keyPassword=$(& $esc $pw)"
) -join "`n"
[IO.File]::WriteAllText((Join-Path $root 'keystore.properties'), $props + "`n")
Write-Host 'Wrote keystore.properties (gitignored).'

# --- 3. GitHub secrets -----------------------------------------------------------------------
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host 'Installing GitHub CLI with winget...'
    winget install --id GitHub.cli -e
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}
gh auth status *> $null
if ($LASTEXITCODE -ne 0) { gh auth login --hostname github.com --git-protocol https --web }

function Set-Secret([string]$name, [string]$value) {
    $value | gh secret set $name --repo $repo
    if ($LASTEXITCODE -ne 0) { throw "Could not set $name" }
}
Set-Secret 'SEERFLIX_KEYSTORE_BASE64' ([Convert]::ToBase64String([IO.File]::ReadAllBytes($jks)))
Set-Secret 'SEERFLIX_KEYSTORE_PASSWORD' $pw
Set-Secret 'SEERFLIX_KEY_ALIAS' $alias
Set-Secret 'SEERFLIX_KEY_PASSWORD' $pw

$localProps = Join-Path $root 'local.properties'
$trakt = if (Test-Path $localProps) {
    (Get-Content $localProps | Where-Object { $_ -match '^\s*trakt\.clientId\s*=' } | Select-Object -First 1) -replace '^[^=]*=\s*', ''
}
if ($trakt) { Set-Secret 'TRAKT_CLIENT_ID' $trakt.Trim() } else { Write-Host 'No trakt.clientId in local.properties - skipped TRAKT_CLIENT_ID.' }

$pw = $null; $pw2 = $null
Write-Host ''
Write-Host 'Done. GitHub secrets set:'
gh secret list --repo $repo
Write-Host ''
Write-Host "BACK UP $jks and its password (password manager + a copy of the file somewhere off this PC)."
