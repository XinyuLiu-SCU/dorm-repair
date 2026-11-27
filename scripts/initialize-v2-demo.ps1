$ErrorActionPreference='Stop'
if(-not $env:V2_DB_USER){throw 'Set V2_DB_USER first'}
$repoRoot=Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $repoRoot 'backend')
try {
 & mvn.cmd -q -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/v2-demo-classpath.txt' '-DincludeScope=runtime'
 if($LASTEXITCODE -ne 0){throw 'Cannot prepare demo tool'}
 $demoCp='target/classes;'+(Get-Content target/v2-demo-classpath.txt -Raw).Trim()
 & java -cp $demoCp com.dormrepair.v2.catalog.DemoTool
 if($LASTEXITCODE -ne 0){throw 'Synthetic demo refused; no automatic repair'}
} finally {Pop-Location}
