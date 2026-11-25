$ErrorActionPreference='Stop'
$frontend=Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend'
if(-not(Test-Path (Join-Path $frontend '.env.local'))){Copy-Item -LiteralPath (Join-Path $frontend '.env.example') -Destination (Join-Path $frontend '.env.local')}
if(-not(Test-Path (Join-Path $frontend 'node_modules'))){throw 'Run npm.cmd --prefix frontend ci from repository root first'}
Push-Location $frontend
try { & npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort; if($LASTEXITCODE -ne 0){throw 'Frontend startup failed'} }
finally { Pop-Location }
