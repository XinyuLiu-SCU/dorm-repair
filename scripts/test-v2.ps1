$ErrorActionPreference='Stop'
if(-not $env:V2_DB_USER){throw 'Set V2_DB_USER first; run prepare-v2.ps1 -IncludeRegression before the first test'}
$env:FLOWABLE_TEST_DB_USER=$env:V2_DB_USER
$env:FLOWABLE_TEST_DB_PASSWORD=$env:V2_DB_PASSWORD
$env:FLOWABLE_TEST_DB_NAME='dorm_repair_v2_test_v1'
$env:FLOWABLE_REHEARSAL_DB_NAME='dorm_repair_v2_test_rehearsal'
$repoRoot=Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $repoRoot 'backend')
try {
 & mvn.cmd -B test '-Dflowable.stageA=true' '-Dflowable.stageB=true' '-Dflowable.stageC=true' '-Dflowable.stageD=true' '-Dflowable.v2A=true' '-Dflowable.v2B=true'
 if($LASTEXITCODE -ne 0){throw 'V2 foundation or V1 regression failed'}
} finally {Pop-Location}
