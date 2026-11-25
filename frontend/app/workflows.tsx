'use client'
import { Button,Field,Input,Select,Spinner } from '@fluentui/react-components'
import { useState } from 'react'
import { day,department,Editor,endpoint,Panel,Records,save,Table,useRows,type Context,type Row,type Spec,type Values } from './shared'

const statuses=['ACTIVE','PROBATION','NOTICE_PERIOD','EXITED']
export function Employees({ctx}:{ctx:Context}){
  const [search,setSearch]=useState('')
  const [filter,setFilter]=useState('')
  const [edit,setEdit]=useState<Row|null>(null)
  const [history,setHistory]=useState('')
  const [initialize,setInitialize]=useState(false)
  const fresh=edit&&!edit.id
  const fields:Spec[]=[...(fresh?[[ 'id','Employee number' ] as Spec]:[]),[ 'name','Full name' ],department(ctx.departments),
    [ 'job','Job title' ],[ 'level','Job level?' ],[ 'demographic','Demographic category?' ],
    [ 'workLocation','Work location?' ],[ 'employmentType','Employment type?' ],
    [ 'status','Status','select',fresh?statuses.slice(0,3):statuses ],[ 'effectiveOn','Effective date','date' ]]
  const admin=ctx.role==='HR_ADMIN'
  return <>{admin&&<Button appearance="primary" onClick={()=>setEdit({})}>New employee</Button>}
    {edit&&<Panel title={fresh?'Create employee':'Edit employee '+edit.id}>
      <Editor key={String(edit.id??'new')} fields={fields} initial={{...(edit as Values),status:String(edit.status??'ACTIVE'),effectiveOn:day()}}
        submit={fresh?'Create employee':'Save changes'} onSave={async data=>{
          await save(ctx,fresh?'/employees':endpoint('/employees',edit.id),fresh?'POST':'PUT')(data);setEdit(null)
        }}/><Button appearance="subtle" onClick={()=>setEdit(null)}>Cancel</Button></Panel>}
    <Panel title="Employee directory"><div className="tools">
      <Field label="Search"><Input value={search} placeholder="Name, number or job" onChange={(_,d)=>setSearch(d.value)}/></Field>
      <Field label="Department"><Select value={filter} onChange={e=>setFilter(e.target.value)}><option value="">All in scope</option>
        {ctx.departments.map(d=><option key={d}>{d}</option>)}</Select></Field></div>
      <Records ctx={ctx} path={'/employees?'+new URLSearchParams({search,department:filter})}
        columns={[[ 'name','Name' ],[ 'id','Number' ],[ 'departmentId','Department' ],[ 'job','Job' ],[ 'level','Level' ],
          [ 'workLocation','Location' ],[ 'employmentType','Employment type' ],[ 'status','Status' ]]}
        action={r=><div className="tools"><Button size="small" onClick={()=>{setHistory(String(r.id));setInitialize(false)}}>History</Button>
          {admin&&<Button size="small" onClick={()=>setEdit(r)}>Edit</Button>}</div>}/></Panel>
    {history&&<Panel title={'Employment history · '+history}><Records ctx={ctx} path={endpoint('/employees',history,'/history')}
      columns={[[ 'effective_on','Date' ],[ 'kind','Event' ],[ 'department_id','Department' ],[ 'status','Status' ]]}/>
      {admin&&!initialize&&<Button onClick={()=>setInitialize(true)}>Initialize missing history</Button>}
      {admin&&initialize&&<Editor fields={[[ 'effectiveOn','Observed on','date' ]]} initial={{effectiveOn:day()}}
        submit="Record verified snapshot" onSave={save(ctx,endpoint('/employees',history,'/history'))}/>}
      <Button onClick={()=>{setHistory('');setInitialize(false)}}>Close history</Button></Panel>}</>
}

const nextStages:Record<string,string[]>={APPLIED:['SCREENING','INTERVIEW','OFFERED','REJECTED','WITHDRAWN'],
  SCREENING:['INTERVIEW','OFFERED','REJECTED','WITHDRAWN'],INTERVIEW:['OFFERED','REJECTED','WITHDRAWN'],
  OFFERED:['ACCEPTED','DECLINED','WITHDRAWN'],ACCEPTED:['HIRED']}
export function Recruitment({ctx}:{ctx:Context}){
  const [form,setForm]=useState('')
  const [selected,select]=useState<Row>()
  const list=useRows(ctx.call,'/applications',ctx.revision)
  const latest=Array.from(new Map(list.rows.map(row=>[row.application_id,row])).values())
  const admin=ctx.role==='HR_ADMIN'
  const choices=(stage:unknown)=>(nextStages[String(stage)]??[]).filter(s=>admin||!['OFFERED','ACCEPTED','DECLINED','HIRED'].includes(s))
  return <>{admin&&<div className="tools">
    <Button appearance="primary" onClick={()=>{select(undefined);setForm('req')}}>Open requisition</Button>
    <Button onClick={()=>{select(undefined);setForm('candidate')}}>Link candidate</Button></div>}
    {form==='req'&&<Panel title="Open requisition"><Editor fields={[[ 'id','Requisition code' ],department(ctx.departments),
      [ 'job','Job title' ],[ 'responsible','Responsible username' ],[ 'openedOn','Opened on','date' ]]} initial={{openedOn:day(),responsible:ctx.username}}
      submit="Save requisition" onSave={save(ctx,'/requisitions')}/><Button onClick={()=>setForm('')}>Close form</Button></Panel>}
    {form==='candidate'&&<Panel title="Link candidate"><Editor fields={[[ 'id','Application code' ],[ 'requisitionId','Requisition code' ],
      [ 'candidateId','Candidate code' ],[ 'candidateName','Candidate name' ],[ 'appliedOn','Applied on','date' ]]} initial={{appliedOn:day()}}
      submit="Save application" onSave={save(ctx,'/applications')}/><Button onClick={()=>setForm('')}>Close form</Button></Panel>}
    {selected&&<Panel title={'Update application · '+selected.application_id}><Editor key={String(selected.application_id)}
      fields={[[ 'stage','Next stage','select',choices(selected.stage) ],[ 'date','Stage date','date' ],[ 'reason','Rejection reason or interview notes?' ],
        ...(selected.stage==='ACCEPTED'&&admin?[[ 'employeeId','Employee number for hire' ],[ 'jobLevel','Job level for hire?' ]] as Spec[]:[])]}
      initial={{date:day()}} submit="Record stage" onSave={async data=>{
        await save(ctx,endpoint('/applications',selected.application_id,'/stage'))(data);select(undefined)
      }}/><Button onClick={()=>select(undefined)}>Cancel</Button></Panel>}
    <Panel title="Requisitions"><Records ctx={ctx} path="/requisitions" columns={[[ 'requisition_id','Code' ],
      [ 'department_id','Department' ],[ 'job','Job' ],[ 'opened_on','Opened' ],[ 'responsible_username','Responsible' ]]}/></Panel>
    <Panel title="Current applications">{list.busy?<Spinner size="tiny" label="Loading applications"/>:list.error?
      <p className="error" role="alert">{list.error}</p>:<Table rows={latest} columns={[[ 'application_id','Application' ],
        [ 'full_name','Candidate' ],[ 'requisition_id','Requisition' ],[ 'stage','Stage' ],[ 'effective_on','Stage date' ],[ 'employee_id','Employee' ]]}
        action={r=>!!choices(r.stage).length&&<Button size="small" onClick={()=>{setForm('');select(r)}}>Update stage</Button>}/>}</Panel>
    <Panel title="Application history"><Table rows={list.rows} columns={[[ 'application_id','Application' ],
      [ 'stage','Stage' ],[ 'effective_on','Date' ],[ 'reason','Notes' ]]}/></Panel>
    <Panel title="Candidate directory"><Records ctx={ctx} path="/candidates" columns={[[ 'candidateId','Candidate code' ],
      [ 'name','Candidate' ],[ 'departmentId','Department' ]]}/></Panel></>
}

export function Compensation({ctx}:{ctx:Context}){
  return <>{ctx.role==='HR_ADMIN'&&<>
    <Panel title="Record base pay"><Editor fields={[[ 'employeeId','Employee number' ],[ 'effectiveOn','Effective date','date' ],
      [ 'amount','Base pay','number' ],[ 'currency','Currency' ],[ 'basis','Pay basis','select',['HOURLY','MONTHLY','ANNUAL'] ],
      [ 'fte','FTE','number' ]]} initial={{effectiveOn:day(),fte:1}} submit="Save base pay" onSave={save(ctx,'/compensation')}/></Panel>
    <Panel title="Record promotion"><Editor fields={[[ 'employeeId','Employee number' ],[ 'effectiveOn','Effective date','date' ],
      [ 'level','New job level' ]]} initial={{effectiveOn:day()}} submit="Save promotion" onSave={save(ctx,'/promotions')}/></Panel></>}
    <Panel title="Base pay history"><Records ctx={ctx} path="/compensation" columns={[[ 'employeeId','Employee' ],
      [ 'departmentId','Department' ],[ 'effectiveOn','Effective date' ],[ 'amount','Base pay' ],[ 'currency','Currency' ],[ 'basis','Basis' ],
      [ 'fte','FTE' ]]}/></Panel>
    {ctx.role!=='FINANCE'&&<Panel title="Promotion history"><Records ctx={ctx} path="/promotions" columns={[[ 'employee_id','Employee' ],
      [ 'department_id','Department' ],[ 'effective_on','Date' ],[ 'prior_level','Prior level' ],[ 'new_level','New level' ]]}/></Panel>}</>
}
