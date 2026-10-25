# Starts the whole Synapse stack on Windows: database, AI model check, backend and frontend.
# Usage (from the repo root):  powershell -ExecutionPolicy Bypass -File .\start.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path .env)) {
    Write-Host 'Creating .env with fresh local secrets...'
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 48; $rng.GetBytes($bytes)
    $jwt = [Convert]::ToBase64String($bytes)
    $pwBytes = New-Object byte[] 24; $rng.GetBytes($pwBytes)
    $pw = [Convert]::ToBase64String($pwBytes) -replace '[+/=]', ''
    @(
        '# Local development secrets for Synapse. Never commit this file.',
        'POSTGRES_DB=synapse',
        'POSTGRES_USER=synapse',
        "POSTGRES_PASSWORD=$pw",
        'SYNAPSE_DB_PORT=5433',
        "SYNAPSE_JWT_SECRET=$jwt",
        'OLLAMA_BASE_URL=http://localhost:11434',
        'OLLAMA_MODEL=qwen2.5-coder:7b'
    ) | Set-Content -Encoding ascii .env
}

Write-Host '[1/4] Starting PostgreSQL (Docker)...'
docker compose up -d | Out-Null

Write-Host '[2/4] Checking the local AI model...'
$model = (Get-Content .env | Where-Object { $_ -like 'OLLAMA_MODEL=*' }) -replace 'OLLAMA_MODEL=', ''
if (Get-Command ollama -ErrorAction SilentlyContinue) {
    if (-not (ollama list | Select-String -SimpleMatch $model)) {
        Write-Host "      Pulling $model (one-time download, about 4.7 GB)..."
        ollama pull $model
    }
} else {
    Write-Warning 'Ollama is not installed. Analyses will run, but AI documentation will be skipped. Get it from https://ollama.com'
}

if (-not (Test-Path node_modules)) {
    Write-Host '      Installing frontend dependencies...'
    npm install
}

Write-Host '[3/4] Starting the backend on http://localhost:8080 (new window)...'
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$PSScriptRoot\backend'; .\mvnw.cmd spring-boot:run"

Write-Host '[4/4] Starting the frontend on http://localhost:5173/synapse/ ...'
npm run dev
