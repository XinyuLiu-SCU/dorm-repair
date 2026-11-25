$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
if(-not $env:INTEGRATION_JWT_SECRET){
    $bytes=New-Object byte[] 32
    $rng=[System.Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
    $env:INTEGRATION_JWT_SECRET=[Convert]::ToBase64String($bytes)
}
if(-not $env:CORS_ORIGINS){$env:CORS_ORIGINS='http://localhost:5173,http://127.0.0.1:5173'}
Push-Location (Join-Path $repoRoot 'backend')
try { & mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=integration'; if($LASTEXITCODE -ne 0){throw 'Backend startup failed'} }
finally { Pop-Location }
