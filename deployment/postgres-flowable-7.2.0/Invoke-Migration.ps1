param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z][a-z0-9_]{0,62}$')][string]$Database,
    [switch]$VerifyOnly,
    [string]$HostName='localhost',
    [string]$UserName='sota',
    [string]$PsqlPath='D:\pgsql\bin\psql.exe'
)
$ErrorActionPreference='Stop'
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFEST.json') -Raw | ConvertFrom-Json
foreach($entry in $manifest) {
    $actual=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $entry.file) -Algorithm SHA256).Hash.ToLowerInvariant()
    if($actual -ne $entry.packagedSha256) { throw "Migration checksum mismatch: $($entry.file)" }
}
if($VerifyOnly) { Write-Output 'All migration checksums verified; no database connection made'; return }
# Run only after the target database migration has been explicitly approved.
& $PsqlPath -h $HostName -U $UserName -w -d $Database -v "expected_db=$Database" -f (Join-Path $PSScriptRoot 'apply.sql')
if($LASTEXITCODE -ne 0) { throw "Migration failed; do not activate workflow. psql exit code $LASTEXITCODE" }
& $PsqlPath -h $HostName -U $UserName -w -d $Database -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot 'verify.sql')
if($LASTEXITCODE -ne 0) { throw 'Post-migration verification failed' }
