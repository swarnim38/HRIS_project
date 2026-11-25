param([switch]$Setup,[switch]$Demo,[switch]$BuildOnly)

$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    $config = Join-Path $PSScriptRoot 'talentcompass.local.properties'
    if ($Setup -and !(Test-Path -LiteralPath $config)) {
        if (!$Demo) { throw 'Create talentcompass.local.properties for your organization, or use -Setup -Demo for a synthetic database.' }
        $properties = @(
            'SPRING_PROFILES_ACTIVE=demo'
            ('HRIS_DB_PASSWORD=' + [guid]::NewGuid().ToString('N'))
            ('HRIS_DEMO_PASSWORD=' + [guid]::NewGuid().ToString('N'))
            'HRIS_START_MYSQL=true'
            'HRIS_COMPOSE_PROJECT=talentcompass-demo'
            'HRIS_MYSQL_PORT=3308'
        )
        [IO.File]::WriteAllLines($config,[string[]]$properties)
    }
    if ($Setup) {
        & pnpm --dir frontend install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependencies failed to install' }
        & pnpm --dir frontend run build
        if ($LASTEXITCODE -ne 0) { throw 'The browser build failed' }
        $maven = Get-Command mvn -ErrorAction SilentlyContinue
        if (!$maven) {
            $cache = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
            if (Test-Path -LiteralPath $cache) {
                $maven = Get-ChildItem -LiteralPath $cache -Filter mvn.cmd -Recurse | Select-Object -First 1
            }
        }
        if (!$maven) { throw 'Install Maven or add its executable to PATH' }
        $command = if ($maven.Source) { $maven.Source } else { $maven.FullName }
        & $command -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'The backend build failed' }
    }
    if (!$BuildOnly) {
        if (!(Test-Path -LiteralPath 'frontend/out/index.html') -or !(Test-Path -LiteralPath 'target/talentcompass-0.2.0.jar')) {
            throw 'Run .\Start-TalentCompass.ps1 -Setup -Demo once to build the browser and backend.'
        }
        & java -jar target/talentcompass-0.2.0.jar
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
} finally { Pop-Location }
