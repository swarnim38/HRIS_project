param(
    [string]$Base='http://127.0.0.1:8080',
    [string]$Java='java',
    [Parameter(Mandatory)][string]$DemoPassword
)
$ErrorActionPreference='Stop'
$classes=(Resolve-Path (Join-Path $PSScriptRoot '../target/classes')).Path
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('tc-reconcile-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
$files=@{
    'departments.csv'=@('OrgId,DepartmentId,DepartmentName','DEMO-A,ENG,Engineering','DEMO-A,OPS,Operations','DEMO-B,ENG,Engineering')
    'startup.csv'=@('OrgId,DepartmentId,ID,FullName,Demographic,Role,JobLevel,JoiningDate,BasicSalary,Currency,PayBasis,FTE,Status',
        'DEMO-A,ENG,E1,Synthetic A Engineer,,Engineer,L2,2020-01-01,5000,USD,MONTHLY,1,ACTIVE',
        'DEMO-A,OPS,E2,Synthetic A Analyst,,Analyst,L1,2021-01-01,4500,USD,MONTHLY,1,PROBATION',
        'DEMO-B,ENG,E1,Synthetic B Engineer,,Engineer,L1,2022-01-01,7000,USD,MONTHLY,1,ACTIVE')
    'employment-events.csv'=@('OrgId,EmployeeId,DepartmentId,Date,Kind,Status',
        'DEMO-A,E1,ENG,2020-01-01,HIRE,ACTIVE','DEMO-A,E2,OPS,2021-01-01,HIRE,PROBATION','DEMO-B,E1,ENG,2022-01-01,HIRE,ACTIVE')
    'employment-coverage.csv'=@('OrgId,DepartmentId,From,Through','DEMO-A,ENG,2020-01-01,2025-01-31',
        'DEMO-A,OPS,2020-01-01,2025-01-31','DEMO-B,ENG,2022-01-01,2025-01-31')
}
foreach($file in $files.Keys){[IO.File]::WriteAllLines((Join-Path $fixture $file),[string[]]$files[$file])}
Push-Location $fixture
try {
    $inputLines=@('DEMO-A','1','2','3','2025-01-01','2025-01-31','0')
    $legacy=$inputLines | & $Java -cp $classes LegacyCli --as-of=2025-01-31
    if($LASTEXITCODE -ne 0){throw 'Legacy CLI comparison failed'}
    $legacy=$legacy -join [Environment]::NewLine
    if($legacy -notmatch 'Total: (\d+)'){throw 'Legacy headcount unavailable'}
    $headcount=[int]$Matches[1]
    if($legacy -notmatch 'Turnover: ([\d.]+)%'){throw 'Legacy turnover unavailable'}
    $turnover=[double]$Matches[1]
    $null=Invoke-RestMethod "$Base/api/v1/login" -Method Post -ContentType 'application/json' -SessionVariable client -Body (
        @{organization='DEMO-A';username='a-admin';password=$DemoPassword}|ConvertTo-Json)
    $new=Invoke-RestMethod "$Base/api/v1/dashboard?from=2025-01-01&through=2025-01-31" -WebSession $client
    if(($new.metrics|Where-Object name -eq 'Headcount').value -ne $headcount -or
       ($new.metrics|Where-Object name -eq 'Turnover').value -ne $turnover){
        throw 'Legacy and API results differ for the same dated fixture'
    }
    "Identical two-organization fixture: legacy and browser API agree on $headcount employees and $turnover% turnover."
} finally {
    Pop-Location
    foreach($file in $files.Keys){Remove-Item -LiteralPath (Join-Path $fixture $file)}
    Remove-Item -LiteralPath $fixture
}
