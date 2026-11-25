param(
    [Parameter(Mandatory)][string]$Java,
    [Parameter(Mandatory)][string]$Classes
)

$ErrorActionPreference = 'Stop'
$Java = (Get-Command $Java -ErrorAction Stop).Source
$Classes = (Resolve-Path $Classes).Path
$fixture = Join-Path ([IO.Path]::GetTempPath()) ("talentcompass-cli-" + [guid]::NewGuid())
[void][IO.Directory]::CreateDirectory($fixture)

function Write-Fixture([string]$File, [string]$Contents) {
    [IO.File]::WriteAllText((Join-Path $fixture $File), $Contents.Trim() + [Environment]::NewLine)
}

function Assert-Contains([string]$Output, [string[]]$Expected) {
    foreach ($item in $Expected) {
        if (!$Output.Contains($item)) { throw "Missing expected output: $item" }
    }
}

function Invoke-TalentCompass([string[]]$Choices, [string[]]$Options) {
    $output = ($Choices -join [Environment]::NewLine) | & $Java -cp $Classes LegacyCli @Options
    if ($LASTEXITCODE -ne 0) { throw "CLI returned exit code $LASTEXITCODE" }
    return ($output -join [Environment]::NewLine)
}

Write-Fixture 'departments.csv' @'
OrgId,DepartmentId,DepartmentName
A,eng,Engineering
A,ops,Operations
B,eng,Engineering
'@
Write-Fixture 'startup.csv' @'
OrgId,DepartmentId,ID,FullName,Demographic,Role,JobLevel,JoiningDate,BasicSalary,Currency,PayBasis,FTE,Status
A,eng,E1,"Synthetic ""Quoted"", Name",X,Engineer,L1,2016-01-01,100,USD,MONTHLY,1,Active
A,eng,E2,Sample Eng Two,X,Engineer,L1,2020-01-01,120,USD,MONTHLY,1,Probation
A,eng,E3,Sample Eng Three,Y,Engineer,L1,2020-01-01,160,USD,MONTHLY,1,Notice Period
A,eng,E4,Sample Eng Four,Y,Engineer,L1,2020-01-01,180,USD,MONTHLY,1,Active
A,eng,E5,Sample Former,,Engineer,L1,2020-01-01,90,USD,MONTHLY,1,Exited
A,ops,E1O,Sample Ops Worker,,Operator,,2017-01-01,150,,,,Active
B,eng,E1,Other Tenant Worker,Z,Engineer,L1,2016-01-01,999,USD,MONTHLY,1,Active
'@
Write-Fixture 'requisitions.csv' @'
OrgId,DepartmentId,RequisitionId,Job,OpenedOn
A,eng,R1,Engineer,2025-01-01
A,ops,R2,Operator,2025-01-01
A,eng,R3,Analyst,2025-01-01
B,eng,R1,Engineer,2025-01-01
'@
Write-Fixture 'employment-events.csv' @'
OrgId,EmployeeId,DepartmentId,Date,Kind,Status
A,E1,eng,2016-01-01,HIRE,ACTIVE
A,E2,eng,2020-01-01,HIRE,PROBATION
A,E3,eng,2020-01-01,HIRE,NOTICE_PERIOD
A,E4,eng,2020-01-01,HIRE,ACTIVE
A,E5,eng,2020-01-01,HIRE,ACTIVE
A,E5,eng,2025-01-06,SEPARATION,EXITED
A,E1O,ops,2017-01-01,HIRE,ACTIVE
B,E1,eng,2016-01-01,HIRE,ACTIVE
'@
Write-Fixture 'employment-coverage.csv' @'
OrgId,DepartmentId,From,Through
A,eng,2025-01-01,2025-01-31
A,ops,2025-01-01,2025-01-31
B,eng,2025-01-01,2025-01-31
'@
Write-Fixture 'candidates.csv' 'L1,Synthetic Legacy,Engineer,2025-01-01,2025-01-04,Accepted'
Write-Fixture 'rejections.csv' 'L2,"Synthetic, legacy reason",2025-01-05'
Write-Fixture 'invalid-employees.csv' @'
ID,FullName,Gender,Department,Role,JoiningDate,BasicSalary,Status
ID1,Sample Hidden Name,,Engineering,Engineer,2025-02-30,100,Active
'@
$rejectionHash = (Get-FileHash (Join-Path $fixture 'rejections.csv')).Hash
$candidateHash = (Get-FileHash (Join-Path $fixture 'candidates.csv')).Hash
$flags = @('--as-of=2025-01-31', '--legacy-org=A', '--legacy-recruitment-department=eng',
    '--minimum-group-size=2', '--tenure-years=6')

Push-Location $fixture
try {
    $preview = Invoke-TalentCompass @('') ($flags + '--validate-employees=startup.csv')
    Assert-Contains $preview @('Validation only | Valid rows: 7 | Errors: 0')
    $invalid = & $Java -cp $Classes LegacyCli @flags '--validate-employees=invalid-employees.csv'
    if ($LASTEXITCODE -ne 1) { throw 'Invalid preview must return exit status 1' }
    $invalid = $invalid -join [Environment]::NewLine
    Assert-Contains $invalid @('Validation only | Valid rows: 0 | Errors: 1', 'Row 2 | Joining date')
    if ($invalid.Contains('Sample Hidden Name')) { throw 'Import errors exposed source data' }
    $admin = Invoke-TalentCompass @('A','1','1','2','3','2025-01-01','2025-01-10','4','5','0') $flags
    Assert-Contains $admin @('Employee directory | Rows: 6', 'Synthetic "Quoted", Name', 'Total: 5',
        'Separations: 1', 'Workforce: 5', '6 completed years | 2 flagged')
    if ($admin.Contains('Other Tenant Worker')) { throw 'Admin saw another organization' }

    $manager = Invoke-TalentCompass @('A','2','eng','1','2','4','8',
        '6','2','R2','FORBIDDEN','C0','Synthetic Denied','2025-01-02',
        '6','2','R1','APP','C1','Synthetic, "Candidate"','2025-01-02',
        '6','3','APP','ACCEPTED','2025-01-03',
        '6','3','APP','OFFERED','2025-01-04',
        '6','3','APP','ACCEPTED','2025-01-06',
        '6','2','R3','APP2','C2','Synthetic Rejected','2025-01-02',
        '7','1','APP2','2025-01-04','Synthetic "timing", later',
        '6','4','2025-01-01','2025-01-10',
        '7','2','2025-01-01','2025-01-10','0') $flags
    Assert-Contains $manager @('Employee directory | Rows: 5','Total: 4','AVAILABLE | Workforce: 4',
        'X | n=2', 'Y | n=2', 'Recruitment write is outside', 'Invalid application stage transition',
        'Saved to local CSV.', 'SHRM time-to-fill | Median 5.0 days | n=1',
        'Time-to-hire | Median 4.0 days | n=1', 'Legacy application-to-offer duration | Median 3.0 days | n=1',
        'Acceptance coverage is incomplete', 'Synthetic "timing", later', 'Synthetic, legacy reason')
    if ($manager.Contains('Sample Ops Worker') -or $manager.Contains('Other Tenant Worker')) {
        throw 'Manager saw an out-of-scope employee'
    }

    $finance = Invoke-TalentCompass @('A','3','1','7','8','9','2025-01-01','2025-01-31','0') $flags
    Assert-Contains $finance @('This report requires HR Admin or Manager access', 'Compensation records | Rows: 6',
        'E1O | ops', 'Cost per hire | Unavailable', 'Revenue per employee | Unavailable')
    if ($finance.Contains('Sample Ops Worker') -or $finance.Contains('Synthetic "Quoted"') -or
            $finance.Contains('Quality of hire') -or $finance.Contains('999')) { throw 'Finance saw out-of-scope data' }
    if ((Get-FileHash rejections.csv).Hash -ne $rejectionHash -or (Get-FileHash candidates.csv).Hash -ne $candidateHash) {
        throw 'Legacy files changed'
    }
    'CLI smoke passed for HR Admin, Manager, Finance and validation-only mode.'
} finally {
    Pop-Location
}
