'use client'
import { Button,Field,Select,Textarea } from '@fluentui/react-components'
import { useState } from 'react'
import { day,department,Editor,endpoint,Panel,Records,save,Table,type Context,type Row,type Spec,type Values } from './shared'

export function Inputs({ctx}:{ctx:Context}){
  const [section,setSection]=useState('Finance')
  const options:Record<string,[string,Spec[],string?]>={
    Finance:['/finance',[[ 'from','From','date' ],[ 'through','Through','date' ],[ 'currency','Currency' ],[ 'recruitingCost','Recruiting costs','number' ],
      [ 'employeeCost','Employee costs','number' ],[ 'revenue','Revenue','number' ]]],
    Attendance:['/attendance',[[ 'employeeId','Employee number' ],[ 'date','Scheduled date','date' ],
      [ 'scheduled','Scheduled hours','number' ],[ 'absence','Unplanned absence hours','number' ]]],
    Surveys:['/surveys',[[ 'id','Survey code' ],[ 'version','Version','number' ],department(ctx.departments),
      [ 'kind','Survey category','select',['ENGAGEMENT','SATISFACTION'] ],[ 'question','Question' ]]],
    'Survey scores':['/survey-responses',[[ 'id','Survey code' ],[ 'version','Version','number' ],[ 'date','Response date','date' ],
      [ 'scores','Anonymous scores from 1 to 5, separated by commas','scores' ]]],
    'Hire outcomes':['/outcomes',[[ 'applicationId','Application code' ],[ 'measuredOn','Measured on','date' ],
      [ 'performance','Performance score','number' ],[ 'managerScore','Manager score','number' ],[ 'retained','Retained','select',['Yes','No'] ]]],
    'History coverage':['/history-coverage',[department(ctx.departments),[ 'from','Complete from','date' ],[ 'through','Complete through','date' ]],'PUT'],
    'Metric settings':['/metric-settings',[[ 'surveyMinimum','Minimum survey responses','number' ],[ 'payMinimum','Minimum pay group size','number' ],
      [ 'tenureYears','Tenure review years','number' ],[ 'performanceWeight','Performance weight %','number' ],
      [ 'managerWeight','Manager weight %','number' ],[ 'retentionWeight','Retention weight %','number' ]],'PUT'],
  }
  const [path,fields,method]=options[section]
  return <>{ctx.role==='HR_ADMIN'&&<div className="tools"><Field label="Input type"><Select value={section} onChange={e=>setSection(e.target.value)}>
    {Object.keys(options).map(v=><option key={v}>{v}</option>)}</Select></Field></div>}
    <Panel title={section}>
      {section==='History coverage'&&<p className="muted">Declare dates after verifying the complete employment history for the department.</p>}
      <Editor key={section} fields={fields} submit="Save input" onSave={async data=>{
        if(section==='Hire outcomes')data.retained=data.retained==='Yes'
        await save(ctx,path,method)(data)
      }}/></Panel>
    {section==='Finance'&&<Panel title="Financial periods"><Records ctx={ctx} path="/finance" columns={[[ 'from_date','From' ],
      [ 'through_date','Through' ],[ 'currency','Currency' ],[ 'recruiting_cost','Recruiting costs' ],[ 'employee_cost','Employee costs' ],[ 'revenue','Revenue' ]]}/></Panel>}
    {['Surveys','Survey scores'].includes(section)&&<Panel title="Survey versions"><Records ctx={ctx} path="/surveys"
      columns={[[ 'survey_id','Code' ],[ 'version_no','Version' ],[ 'department_id','Department' ],[ 'kind','Category' ],[ 'question','Question' ]]}/></Panel>}</>
}

type Preview={hash:string;rows:number;payTotals:Record<string,number>;errors:Row[];committed:boolean}
export function Imports({ctx}:{ctx:Context}){
  const [kind,setKind]=useState('EMPLOYEES')
  const [csv,setCsv]=useState('')
  const [preview,setPreview]=useState<Preview>()
  const [payload,setPayload]=useState<Values>({})
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  const fields:Spec[]=kind==='EMPLOYEES'?[[ 'snapshot','Snapshot date','date' ],[ 'currency','Legacy currency?' ],
    [ 'fte','Legacy FTE?','number' ]]:[department(ctx.departments)]
  return <><Panel title="Preview import"><div className="tools"><Field label="CSV layout"><Select value={kind}
    onChange={e=>{setKind(e.target.value);setPreview(undefined)}}><option value="EMPLOYEES">Employee snapshot</option>
    <option value="CANDIDATES">Legacy candidates</option><option value="REJECTIONS">Legacy rejections</option></Select></Field>
    <Field label="CSV file"><input type="file" aria-label="CSV file" accept=".csv,text/csv" onChange={async e=>{
      const file=e.target.files?.[0]
      if(!file)return
      setPreview(undefined)
      if(file.size>1_000_000){setError('Choose a file up to one megabyte.');return}
      try{setCsv(await file.text());setError('')}catch{setError('Could not read file.')}
    }}/></Field></div>
    <Field label="CSV contents" hint="Corrections here apply to the preview copy."><Textarea rows={5} value={csv} onChange={(_,d)=>{
      setCsv(d.value);setPreview(undefined)
    }}/></Field><Editor key={kind} fields={fields} submit="Preview CSV" notice="Preview generated." initial={{snapshot:day()}}
      onSave={async data=>{const input={...data,kind,csv};setPreview(await ctx.call<Preview>('/imports/preview','POST',input));setPayload(input)}}/>
    {error&&<p className="error" role="alert">{error}</p>}</Panel>
    {preview&&<Panel title="Reconciliation"><p>{preview.committed?'Committed':'Valid'} rows <strong>{preview.rows}</strong></p>
      {Object.entries(preview.payTotals).map(([key,total])=><p key={key}>{key} · {total}</p>)}
      {!!preview.errors.length&&<Table rows={preview.errors} columns={[[ 'line','Line' ],[ 'detail','Validation error' ]]}/>}
      {!preview.committed&&!preview.errors.length&&<Button appearance="primary" disabled={busy} onClick={async()=>{
        setBusy(true);setError('')
        try{setPreview(await ctx.call<Preview>('/imports/commit','POST',{...payload,expectedHash:preview.hash}));ctx.changed()}
        catch(e){setError((e as Error).message)}finally{setBusy(false)}
      }}>Commit validated records</Button>}</Panel>}</>
}

export function Administration({ctx}:{ctx:Context}){
  const [error,setError]=useState('')
  const [assignment,setAssignment]=useState<Row>()
  return <><Panel title="Provision account"><Editor fields={[[ 'username','Username' ],[ 'password','Temporary password','password' ],
    [ 'role','Role','select',['HR_ADMIN','MANAGER','FINANCE'] ],department(ctx.departments,true)]} submit="Create account"
    onSave={save(ctx,'/accounts')}/></Panel>
    <Panel title="Add department"><Editor fields={[[ 'id','Department code' ],[ 'name','Department name' ]]}
      submit="Create department" onSave={save(ctx,'/departments')}/></Panel>
    {assignment&&<Panel title={'Edit assignment · '+assignment.username}><Editor key={String(assignment.username)}
      fields={[[ 'role','Role','select',['HR_ADMIN','MANAGER','FINANCE'] ],department(ctx.departments,true)]}
      initial={{role:String(assignment.role),departmentId:String(assignment.department_id??'')}} submit="Save assignment"
      onSave={async data=>{await save(ctx,endpoint('/accounts',assignment.username,'/assignment'),'PUT')(data);setAssignment(undefined)}}/>
      <Button onClick={()=>setAssignment(undefined)}>Cancel</Button></Panel>}
    <Panel title="Local accounts">{error&&<p className="error" role="alert">{error}</p>}<Records ctx={ctx} path="/accounts"
      columns={[[ 'username','Username' ],[ 'role','Role' ],[ 'department_id','Department' ],[ 'active','Active' ]]}
      action={row=>row.active===true&&row.username!==ctx.username&&<div className="tools">
        <Button size="small" onClick={()=>setAssignment(row)}>Edit assignment</Button><Button size="small" onClick={async()=>{
        if(!confirm('Deactivate '+row.username+'?'))return
        try{await ctx.call(endpoint('/accounts',row.username,'/deactivate'),'POST');ctx.changed();setError('')}
        catch(e){setError((e as Error).message)}
      }}>Deactivate</Button></div>}/></Panel>
    <Panel title="Audit history"><Records ctx={ctx} path="/audit" columns={[[ 'occurred_at','When' ],[ 'username','Actor' ],
      [ 'action','Action' ],[ 'entity_id','Record' ]]}/></Panel></>
}
