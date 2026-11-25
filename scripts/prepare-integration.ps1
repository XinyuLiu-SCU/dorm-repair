param([string]$UserName='postgres',[string]$PsqlPath='psql',[switch]$IncludeRehearsal)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
$psql=(Get-Command $PsqlPath -ErrorAction Stop).Source
$createdb=Join-Path (Split-Path $psql -Parent) 'createdb.exe'
$databases=@('dorm_repair_flowable_a_test_20261123')
if($IncludeRehearsal){$databases+='dorm_repair_migration_test_20261123_d'}
foreach($database in $databases){
    $exists=& $psql -h localhost -U $UserName -w -d postgres -At -v ON_ERROR_STOP=1 -c "select count(*) from pg_database where datname='$database'"
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect test databases'}
    if($exists.Trim() -ne '0'){Write-Output "$database already exists: skipped without writes; inspect manually if incomplete";continue}
    & $createdb -h localhost -U $UserName -w $database
    if($LASTEXITCODE -ne 0){throw 'Cannot create isolated database'}
    foreach($file in @('business-fixture.sql',$(if($database -like '*migration*'){'bootstrap-rehearsal.sql'}else{'bootstrap-legacy.sql'}))){
        & $psql -h localhost -U $UserName -w -d $database -v ON_ERROR_STOP=1 -f (Join-Path $repoRoot "backend/src/test/resources/workflow/$file")
        if($LASTEXITCODE -ne 0){throw 'Synthetic test schema preparation failed; no automatic deletion performed'}
    }
    & (Join-Path $repoRoot 'backend/deployment/postgres-flowable-7.2.0/Invoke-Migration.ps1') -Database $database -UserName $UserName -PsqlPath $psql
}
