param([string]$PsqlPath='psql',[switch]$IncludeRegression)
$ErrorActionPreference='Stop'
if(-not $env:V2_DB_USER){throw 'Set V2_DB_USER; use V2_DB_PASSWORD or a local pgpass/trust rule, never commit credentials'}
$repoRoot=Split-Path $PSScriptRoot -Parent
$psql=(Get-Command $PsqlPath -ErrorAction Stop).Source
$createdb=Join-Path (Split-Path $psql -Parent) 'createdb.exe'
$oldPgPassword=$env:PGPASSWORD
try {
 if($env:V2_DB_PASSWORD){$env:PGPASSWORD=$env:V2_DB_PASSWORD}
 $databases=@('dorm_repair_v2_dev','dorm_repair_v2_test','dorm_repair_v2_test_legacy','dorm_repair_v2_test_failure','dorm_repair_v2_test_rbac','dorm_repair_v2_test_quality','dorm_repair_v2_test_foundationquality','dorm_repair_v2_test_demo','dorm_repair_v2_test_grants','dorm_repair_v2_test_qualityd','dorm_repair_v2_test_foundationd','dorm_repair_v2_test_preparation','dorm_repair_v2_test_grantsd','dorm_repair_v2_test_migrationhttpd')
 if($IncludeRegression){$databases+=@('dorm_repair_v2_test_v1','dorm_repair_v2_test_rehearsal')}
 foreach($database in $databases){
  if($database -notmatch '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$'){throw 'Rejected unsafe target'}
  $exists=& $psql -h 127.0.0.1 -p 5432 -U $env:V2_DB_USER -w -d postgres -At -v ON_ERROR_STOP=1 -c "select count(*) from pg_database where datname='$database'"
  if($LASTEXITCODE -ne 0){throw 'Cannot inspect isolated database catalog'}
  if($exists.Trim() -eq '0'){& $createdb -h 127.0.0.1 -p 5432 -U $env:V2_DB_USER -w $database;if($LASTEXITCODE -ne 0){throw 'Create isolated database failed'};Write-Output "Created $database"}
  else {Write-Output "$database exists; no recreation or reset"}
 }
 if($IncludeRegression){
  foreach($database in @('dorm_repair_v2_test_v1','dorm_repair_v2_test_rehearsal')){
   $tables=& $psql -h 127.0.0.1 -U $env:V2_DB_USER -w -d $database -At -v ON_ERROR_STOP=1 -c "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'"
   if($LASTEXITCODE -ne 0){throw 'Cannot inspect regression schema'}
   if($tables.Trim() -ne '0'){Write-Output "$database already has tables; fixture initialization skipped";continue}
   $seed=if($database -like '*rehearsal'){'bootstrap-rehearsal.sql'}else{'bootstrap-legacy.sql'}
   foreach($name in @('business-fixture.sql',$seed)){
    & $psql -h 127.0.0.1 -U $env:V2_DB_USER -w -d $database -v ON_ERROR_STOP=1 -f (Join-Path $repoRoot "backend/src/test/resources/workflow/$name")
    if($LASTEXITCODE -ne 0){throw 'Synthetic fixture failed; no automatic reset'}
   }
   & (Join-Path $repoRoot 'backend/deployment/postgres-flowable-7.2.0/Invoke-Migration.ps1') -Database $database -UserName $env:V2_DB_USER -PsqlPath $psql
  }
 }
} finally {$env:PGPASSWORD=$oldPgPassword}
