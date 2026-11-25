param(
    [string]$Base = 'http://127.0.0.1:18087',
    [Parameter(Mandatory)][string]$DemoPassword,
    [switch]$AfterRestart,
    [switch]$AfterBackup
)
$ErrorActionPreference='Stop'
$checks=0
function Assert-Release([bool]$Condition,[string]$Message) {
    if (!$Condition) { throw $Message }
    $script:checks++
}
function Login($Org,$User) {
    $r = Invoke-RestMethod "$Base/api/v1/login" -Method Post -ContentType 'application/json' -SessionVariable client -Body (
        @{organization=$Org;username=$User;password=$DemoPassword}|ConvertTo-Json)
    return @{Cookie=$client;Headers=@{'X-CSRF-Token'=$r.csrf};Role=$r.role}
}
function Api($Client,$Path,$Method='GET',$Body=$null) {
    $p=@{Uri="$Base/api/v1$Path";Method=$Method;WebSession=$Client.Cookie;Headers=$Client.Headers;UseBasicParsing=$true}
    if($null -ne $Body){$p.ContentType='application/json';$p.Body=ConvertTo-Json -Depth 8 -InputObject $Body -Compress}
    $reply=Invoke-WebRequest @p
    if($reply.Content){ return $reply.Content | ConvertFrom-Json }
}
function Denied($Client,$Path,$Expected,$Method='GET',$Body=$null) {
    try { $null=Api $Client $Path $Method $Body }
    catch {
        if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Expected) {
            Assert-Release $true "$Path returns $Expected"
            return
        }
        throw
    }
    throw "$Path should have returned $Expected"
}
function Report($Client,$From='2025-01-01',$Through='2025-01-31') {
    Api $Client "/dashboard?from=$From&through=$Through"
}
function Card($Report,$Name) { $Report.metrics|Where-Object name -eq $Name }
function Value($Report,$Name,$Expected) {
    $m=Card $Report $Name
    Assert-Release ($null -ne $m.value -and [math]::Abs($m.value-$Expected) -lt 0.001) "$Name expected $Expected, got $($m.value) [$($m.status)]"
}
function Employee($Id,$Department,$Name,$Date,$Category=$null) {
    @{id=$Id;departmentId=$Department;name=$Name;job='Test role';level='L1';demographic=$Category;effectiveOn=$Date;status='ACTIVE'}
}
$admin=Login DEMO-A a-admin
if ($AfterBackup) {
    Api $admin '/employees' POST (Employee TC-AFTER-BACKUP ENG 'Synthetic after backup' '2025-04-03')
    Assert-Release (@(Api $admin '/employees?search=TC-AFTER-BACKUP').Count -eq 1) 'Post-backup edit is visible before rollback'
    return
}
if ($AfterRestart) {
    $saved=@(Api $admin '/employees?search=TC-HIRE')[0]
    Assert-Release ($saved.id -eq 'TC-HIRE') 'Hired employee survives restart'
    $audit=@(Api $admin '/audit')
    Assert-Release (@($audit|Where-Object {$_.action -eq 'APPLICATION_HIRED' -and $_.entity_id -eq 'TC-APP'}).Count -eq 1) 'Hire audit survives restart'
    $report=Report $admin
    Value $report 'Time to fill' 8
    Value $report 'Headcount' 3
    Assert-Release (@(Api $admin '/employees?search=TC-AFTER-BACKUP').Count -eq 0) 'Backup excludes the later edit'
    "$checks persistence assertions passed."
    return
}
$manager=Login DEMO-A a-manager
$finance=Login DEMO-A a-finance
$other=Login DEMO-B b-admin
$anonymous=@{Cookie=(New-Object Microsoft.PowerShell.Commands.WebRequestSession);Headers=@{}}
Denied $anonymous '/employees' 401
Denied $finance '/employees' 403
Denied $finance '/departments' 403
Denied $finance '/audit' 403
Denied $manager '/accounts' 403
Denied $manager '/employees' 403 POST (Employee TC-DENIED ENG 'Denied' '2025-01-02')
$badCsrf=@{Cookie=$admin.Cookie;Headers=@{}}
Denied $badCsrf '/departments' 403 POST @{id='DENIED';name='Denied'}
Assert-Release (@(Api $admin '/employees').Count -eq 2) 'HR Admin sees two initial employees'
Assert-Release (@(Api $manager '/employees').Count -eq 1) 'Manager sees one department'
Assert-Release (@(Api $finance '/compensation').Count -eq 2) 'Finance sees two pay records'
Assert-Release ((@(Api $other '/employees')[0]).name -eq 'Synthetic B Engineer') 'Overlapping E1 stays in tenant'

$initial=Report $admin
Value $initial Headcount 2
Value $initial Turnover 0
Value $initial Engagement 4
Value $initial Absenteeism 12.5
Value $initial 'Time since last promotion' 30
Value $initial 'Cost per employee' 5000
Value $initial 'Revenue per employee' 15000
Assert-Release ((Card $initial Satisfaction).status -eq 'SUPPRESSED') 'Small surveys are suppressed'
Assert-Release ((Card $initial 'Cost per hire').status -eq 'ZERO_DENOMINATOR') 'Zero hires is distinguished'
Assert-Release ((Card (Report $admin '2019-01-01' '2019-01-31') Headcount).status -eq 'MISSING_INPUTS') 'Missing dated workforce'
Assert-Release ((Card (Report $admin '2025-01-07' '2025-01-31') Engagement).status -eq 'MISSING_INPUTS') 'Survey period gating'

$transfer=Employee E1 OPS 'Synthetic A Engineer' '2025-01-15'
Api $admin '/employees/E1' PUT $transfer
$before=Report $admin '2025-01-14' '2025-01-14'
$after=Report $admin '2025-01-15' '2025-01-15'
Assert-Release ($before.departments.ENG -eq 1 -and $before.departments.OPS -eq 1) 'Headcount before transfer'
Assert-Release ($after.departments.OPS -eq 2 -and !$after.departments.ENG) 'Headcount after transfer'
Assert-Release (@(Api $manager '/employees').Count -eq 0) 'Manager loses current directory access after transfer'
Assert-Release (@(Api $manager '/employees/E1/history').Count -eq 1) 'Only historical record in department is visible'
Assert-Release (@(Api $other '/employees/E1/history').Count -eq 1) 'No cross-tenant history'

$req=@{id='TC-REQ';departmentId='ENG';job='Test engineer';openedOn='2025-01-02';responsible='a-manager'}
Api $admin '/requisitions' POST $req
$app=@{id='TC-APP';requisitionId='TC-REQ';candidateId='TC-CAND';candidateName='Synthetic Hire';appliedOn='2025-01-04'}
Api $admin '/applications' POST $app
Api $manager '/applications/TC-APP/stage' POST @{stage='INTERVIEW';date='2025-01-05';reason='Interview completed'}
Denied $manager '/applications/TC-APP/stage' 403 POST @{stage='OFFERED';date='2025-01-08'}
Api $admin '/applications/TC-APP/stage' POST @{stage='OFFERED';date='2025-01-08'}
Api $admin '/applications/TC-APP/stage' POST @{stage='ACCEPTED';date='2025-01-10'}
$hire=@{stage='HIRED';date='2025-01-21';employeeId='TC-HIRE';jobLevel='L1'}
Api $admin '/applications/TC-APP/stage' POST $hire
Api $admin '/applications/TC-APP/stage' POST $hire
Assert-Release (@(Api $admin '/employees?search=TC-HIRE').Count -eq 1) 'Retry creates exactly one employee'
Api $admin '/applications' POST @{id='TC-REJ';requisitionId='TC-REQ';candidateId='TC-R';candidateName='Synthetic rejection';appliedOn='2025-01-04'}
Api $manager '/applications/TC-REJ/stage' POST @{stage='REJECTED';date='2025-01-06';reason='Skills gap'}
Api $admin '/requisitions' POST @{id='TC-OTHER-REQ';departmentId='OPS';job='Operations analyst';openedOn='2025-01-02';responsible='a-admin'}
Api $admin '/applications' POST @{id='TC-DECLINE';requisitionId='TC-OTHER-REQ';candidateId='TC-D';candidateName='Decline example';appliedOn='2025-01-04'}
Denied $manager '/applications/TC-DECLINE/stage' 403 POST @{stage='SCREENING';date='2025-01-05'}
Api $admin '/applications/TC-DECLINE/stage' POST @{stage='OFFERED';date='2025-01-08'}
Api $admin '/applications/TC-DECLINE/stage' POST @{stage='DECLINED';date='2025-01-10'}
Denied $admin '/applications/TC-DECLINE/stage' 400 POST @{stage='ACCEPTED';date='2025-01-11'}
Api $admin '/applications' POST @{id='TC-WITHDRAW';requisitionId='TC-OTHER-REQ';candidateId='TC-W';candidateName='Withdrawal example';appliedOn='2025-01-04'}
Api $admin '/applications/TC-WITHDRAW/stage' POST @{stage='WITHDRAWN';date='2025-01-06';reason='Candidate withdrew'}
Denied $admin '/applications/TC-WITHDRAW/stage' 400 POST @{stage='OFFERED';date='2025-01-07'}
$stageRows=@(Api $admin '/applications')
Assert-Release (@($stageRows|Where-Object {$_.application_id -eq 'TC-DECLINE' -and $_.stage -eq 'DECLINED'}).Count -eq 1) 'Decline is retained'
Assert-Release (@($stageRows|Where-Object {$_.application_id -eq 'TC-WITHDRAW' -and $_.stage -eq 'WITHDRAWN'}).Count -eq 1) 'Withdrawal is retained'
Api $admin '/outcomes' POST @{applicationId='TC-APP';measuredOn='2025-01-30';performance=80;managerScore=60;retained=$true}
$r=Report $admin
Value $r 'Time to fill' 8
Value $r 'Time to hire' 6
Value $r 'Headcount' 3
Value $r 'Cost per hire' 1200
Value $r 'Quality of hire' 80
Value $r 'Rejections' 1
Value (Report $finance) 'Cost per hire' 1200
Assert-Release ((Report $finance).metrics.Count -eq 3) 'Finance dashboard contains only financial cards'
Api $finance '/finance' POST @{from='2025-01-01';through='2025-01-31';currency='USD';recruitingCost=900;employeeCost=10000;revenue=30000}
Value (Report $finance) 'Cost per hire' 900
Api $admin '/attendance' POST @{employeeId='E1';date='2025-01-06';scheduled=8;absence=0}
Value (Report $admin) Absenteeism 0

$header='ID,FullName,Gender,Department,Role,JoiningDate,BasicSalary,Status'
$invalid="$header`nTC-IMPORT,`"Example, Quoted`",F,Engineering,Analyst,2025-02-01,2100,ACTIVE`nBAD,Faulty,F,Engineering,Analyst,not-a-date,10,ACTIVE"
$input=@{kind='EMPLOYEES';csv=$invalid;snapshot='2025-02-01';currency='USD';fte=1}
$preview=Api $admin '/imports/preview' POST $input
Assert-Release (@($preview.errors).Count -eq 1 -and $preview.errors[0].line -eq 3) 'CSV reports invalid row number'
$input.csv=($invalid -split "`n")[0..1] -join "`n"
$preview=Api $admin '/imports/preview' POST $input
Assert-Release ($preview.rows -eq 1 -and $preview.payTotals.'USD/MONTHLY' -eq 2100) 'CSV preview reconciles valid rows and pay'
$input.expectedHash=$preview.hash
$commit=Api $admin '/imports/commit' POST $input
$retry=Api $admin '/imports/commit' POST $input
Assert-Release ($commit.committed -and $retry.committed -and @(Api $admin '/employees?search=TC-IMPORT').Count -eq 1) 'CSV commit idempotent'
Assert-Release ((@(Api $admin '/employees?search=TC-IMPORT')[0]).name -eq 'Example, Quoted') 'Quoted field survives'
foreach($legacy in @(
    @{kind='CANDIDATES';csv='TC-OLD,"Legacy, Candidate",Engineer,2025-01-12,2025-01-20,ACCEPTED';departmentId='ENG'},
    @{kind='REJECTIONS';csv='TC-OLD,"Not selected, legacy",2025-01-22';departmentId='ENG'}
)) {
    $p=Api $admin '/imports/preview' POST $legacy
    Assert-Release ($p.rows -eq 1 -and !$p.errors.Count) 'Legacy row validates'
    $legacy.expectedHash=$p.hash
    Assert-Release ((Api $admin '/imports/commit' POST $legacy).committed) 'Legacy row committed'
}
Value (Report $admin) 'Legacy application to offer' 8
Value (Report $admin) 'Time to hire' 6
Value (Report $admin) 'Rejections' 2
Value (Report $admin) 'Tenure flags' 1
Api $admin '/employees' POST (Employee TC-PAY-A1 ENG 'Synthetic pay A1' '2025-02-03' 'A')
Api $admin '/employees' POST (Employee TC-PAY-A2 ENG 'Synthetic pay A2' '2025-02-03' 'A')
Api $admin '/employees' POST (Employee TC-PAY-B1 ENG 'Synthetic pay B1' '2025-02-03' 'B')
Api $admin '/employees' POST (Employee TC-PAY-B2 ENG 'Synthetic pay B2' '2025-02-03' 'B')
Api $admin '/employees' POST (Employee TC-PAY-C1 ENG 'Synthetic pay C1' '2025-02-03' 'B')
foreach($id in 'TC-PAY-A1','TC-PAY-A2','TC-PAY-B1','TC-PAY-B2','TC-PAY-C1'){
    $amount=if($id -match '-A'){100}else{120}
    $ccy=if($id -match '-C'){'EUR'}else{'USD'}
    Api $admin '/compensation' POST @{employeeId=$id;effectiveOn='2025-02-03';amount=$amount;currency=$ccy;basis='MONTHLY';fte=1}
}
$pay=Report $admin '2025-02-01' '2025-02-28'
Assert-Release ((Card $pay 'Unadjusted pay comparison').status -eq 'PARTIAL') 'Small incompatible-currency cohort is suppressed'
Assert-Release ($pay.payCohorts.Count -eq 1 -and $pay.payCohorts[0].cohort.currency -eq 'USD') 'Currency cohorts are separate'
Assert-Release ((@($pay.payCohorts[0].groups|Where-Object category -eq 'A')[0]).mean -eq 100) 'Group mean calculated correctly'
Api $admin '/employees/TC-HIRE' PUT (@{departmentId='ENG';name='Synthetic Hire';job='Test engineer';level='L1';effectiveOn='2025-03-02';status='EXITED'})
Api $admin '/employees/TC-HIRE' PUT (@{departmentId='ENG';name='Synthetic Hire';job='Test engineer';level='L1';effectiveOn='2025-03-04';status='ACTIVE'})
$march=Report $admin '2025-03-01' '2025-03-31'
Value $march Turnover (100/(9-2/31.0))
$exited=@{kind='EMPLOYEES';csv="$header`nTC-EXITED,Former Worker,Other,Operations,Analyst,2025-03-15,0,EXITED";
    snapshot='2025-04-01';currency='USD';fte=1}
$p=Api $admin '/imports/preview' POST $exited
$exited.expectedHash=$p.hash
Assert-Release ((Api $admin '/imports/commit' POST $exited).committed) 'Exited legacy snapshots commit'
Assert-Release ((@(Api $admin '/employees/TC-EXITED/history')[0]).kind -eq 'SNAPSHOT') 'Unknown separation date is not fabricated'
Api $admin '/employees/TC-EXITED' PUT (@{departmentId='OPS';name='Former Worker';job='Analyst';effectiveOn='2025-04-04';status='ACTIVE'})
Assert-Release (@(Api $admin '/employees/TC-EXITED/history').Count -eq 2) 'Rehire preserves legacy snapshot'
Api $admin '/accounts' POST @{username='tc-temp';password=$DemoPassword;role='MANAGER';departmentId='OPS'}
$temp=Login DEMO-A tc-temp
Api $admin '/accounts/tc-temp/assignment' PUT @{role='FINANCE';departmentId=$null}
Denied $temp '/employees' 403
Assert-Release (@(Api $temp '/compensation').Count -gt 0) 'Existing session uses the changed assignment'
Api $admin '/accounts/tc-temp/deactivate' POST
Denied $temp '/employees' 401
Api $manager '/logout' POST
Denied $manager '/employees' 401
"$checks live API assertions passed."
