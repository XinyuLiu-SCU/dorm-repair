$ErrorActionPreference='Stop'
if(-not $env:V2_DB_USER -or -not $env:V2_JWT_SECRET){throw 'Set V2_DB_USER and a dedicated V2_JWT_SECRET (>=32 UTF8 bytes); never reuse the production JWT_SECRET'}
if([Text.Encoding]::UTF8.GetByteCount($env:V2_JWT_SECRET) -lt 32){throw 'V2_JWT_SECRET too short'}
$listener=Get-NetTCPConnection -State Listen -LocalPort 18081 -ErrorAction SilentlyContinue
if($listener){throw 'Port 18081 is occupied; no existing process was stopped'}
$repoRoot=Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $repoRoot 'backend')
try{& mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=v2-dev';if($LASTEXITCODE -ne 0){throw 'V2 startup failed'}}finally{Pop-Location}
