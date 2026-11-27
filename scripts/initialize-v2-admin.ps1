$ErrorActionPreference='Stop'
if(-not $env:V2_DB_USER){throw 'Set V2_DB_USER first'}
$repoRoot=Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $repoRoot 'backend')
try {
 & mvn.cmd -q -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/v2-bootstrap-classpath.txt' '-DincludeScope=runtime'
 if($LASTEXITCODE -ne 0){throw 'Cannot prepare bootstrap tool'}
 $bootstrapCp='target/classes;'+(Get-Content target/v2-bootstrap-classpath.txt -Raw).Trim()
 & java -cp $bootstrapCp com.dormrepair.v2.identity.BootstrapAdminTool
 if($LASTEXITCODE -ne 0){throw 'Bootstrap refused or failed; no automatic repair attempted'}
} finally {Pop-Location}
