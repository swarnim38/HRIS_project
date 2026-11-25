param([string]$Java='java',[switch]$SkipBrowser,[switch]$BrowserOnly)
$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
$project='tc-release-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$restored=$project+'-restored'
$secret=[guid]::NewGuid().ToString('N')
$dump=Join-Path ([IO.Path]::GetTempPath()) ($project+'.sql')
$backend=$null
$env:HRIS_DB_PASSWORD=$secret
$env:HRIS_DEMO_PASSWORD=$secret
$env:SPRING_PROFILES_ACTIVE='demo'
$env:HRIS_START_MYSQL='false'
$env:HRIS_PORT='18087'
$env:HRIS_MYSQL_PORT='33317'
$env:HRIS_DB_URL='jdbc:mysql://127.0.0.1:33317/talentcompass'
$base='http://127.0.0.1:18087'

function Native([string]$Executable,[string[]]$Command) {
    & $Executable @Command
    if($LASTEXITCODE -ne 0){throw "$Executable failed with exit code $LASTEXITCODE"}
}
function Start-Backend {
    $out=Join-Path ([IO.Path]::GetTempPath()) ($project+'-app-'+[guid]::NewGuid().ToString('N')+'.log')
    $opts=@{FilePath=$Java;ArgumentList=@('-jar','target/talentcompass-0.2.0.jar');WindowStyle='Hidden';RedirectStandardOutput=$out;RedirectStandardError=($out+'.err');PassThru=$true}
    $script:backend=Start-Process @opts
    for($i=0;$i -lt 50;$i++){
        if($backend.HasExited){Get-Content $out;Get-Content ($out+'.err');throw 'Main exited before readiness'}
        try {
            $health=Invoke-RestMethod "$base/health" -TimeoutSec 2
            if($health.status -eq 'UP'){
                $landing=Invoke-WebRequest "$base/" -UseBasicParsing
                if($landing.StatusCode -ne 200 -or $landing.Content -notmatch 'TalentCompass'){throw 'Login page unavailable'}
                return
            }
        }catch{}
        Start-Sleep -Milliseconds 300
    }
    Get-Content $out
    throw 'Main did not become ready with a working login page'
}
function Stop-Backend {
    if($script:backend -and !$script:backend.HasExited){
        Stop-Process -Id $script:backend.Id
        Wait-Process -Id $script:backend.Id -Timeout 10 -ErrorAction SilentlyContinue
    }
    $script:backend=$null
}
try {
    Native docker @('compose','-p',$project,'up','-d','--wait','--wait-timeout','80')
    Start-Backend
    if(!$BrowserOnly){
        & "$PSScriptRoot\ReleaseApi.ps1" -Base $base -DemoPassword $secret
        Stop-Backend
        Start-Backend
        & "$PSScriptRoot\ReleaseApi.ps1" -Base $base -DemoPassword $secret -AfterRestart
    }
    if(!$SkipBrowser){Native node @('frontend/tests/release.mjs')}
    if(!$BrowserOnly){
        Native docker @('exec',"$project-mysql-1",'sh','-c',
            'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --no-tablespaces --single-transaction --set-gtid-purged=OFF -u "$MYSQL_USER" "$MYSQL_DATABASE" > /tmp/release.sql')
        Native docker @('cp',"${project}-mysql-1:/tmp/release.sql",$dump)
        & "$PSScriptRoot\ReleaseApi.ps1" -Base $base -DemoPassword $secret -AfterBackup
        Stop-Backend
        $env:HRIS_MYSQL_PORT='33318'
        $env:HRIS_DB_URL='jdbc:mysql://127.0.0.1:33318/talentcompass'
        Native docker @('compose','-p',$restored,'up','-d','--wait','--wait-timeout','80')
        Native docker @('cp',$dump,"${restored}-mysql-1:/tmp/release.sql")
        Native docker @('exec',"$restored-mysql-1",'sh','-c',
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/release.sql')
        $sqlCommand='MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM employees"'
        $before= & docker exec "$project-mysql-1" sh -c $sqlCommand
        if($LASTEXITCODE -ne 0){throw 'Original employee count unavailable'}
        $after= & docker exec "$restored-mysql-1" sh -c $sqlCommand
        if($LASTEXITCODE -ne 0 -or [int]$before -ne ([int]$after+1)){throw 'Restored count did not roll back the later edit'}
        Start-Backend
        & "$PSScriptRoot\ReleaseApi.ps1" -Base $base -DemoPassword $secret -AfterRestart
        "Restored $after employees in an independent volume. The later edit was rolled back, and representative reports and audit passed."
    }
} finally {
    Stop-Backend
    $env:HRIS_MYSQL_PORT='33318'
    & docker compose -p $restored stop | Out-Null
    $env:HRIS_MYSQL_PORT='33317'
    & docker compose -p $project stop | Out-Null
    if(Test-Path -LiteralPath $dump){Remove-Item -LiteralPath $dump}
    Pop-Location
}
