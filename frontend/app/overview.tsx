'use client'
import { Badge, Button, Field, Input, Spinner } from '@fluentui/react-components'
import { useEffect,useState } from 'react'
import { day,Panel,Table,type Context,type Row } from './shared'
type Metric={name:string;unit:string;value:number|null;status:string;formula:string;denominator:number|null;coverage:string}
type Report={scope:string;refreshedAt:string;metrics:Metric[];departments:Record<string,number>;rejectionReasons:Row[];
  tenureFlags:Row[];payCohorts:{cohort:Row;groups:Row[]}[]}
export function Overview({ctx}:{ctx:Context}){
  const [from,setFrom]=useState('')
  const [through,setThrough]=useState('')
  const [report,setReport]=useState<Report>()
  const [shown,setShown]=useState('')
  const [busy,setBusy]=useState(true)
  const [error,setError]=useState('')
  async function load(a:string,b:string){
    setError('');setBusy(true)
    try{setReport(await ctx.call<Report>('/dashboard?'+new URLSearchParams({from:a,through:b})));setShown(a+' to '+b)}
    catch(e){setReport(undefined);setError((e as Error).message)}
    finally{setBusy(false)}
  }
  useEffect(()=>{const end=day();const start=end.slice(0,8)+'01';setFrom(start);setThrough(end);void load(start,end)},[])
  return <><form className="tools" onSubmit={e=>{e.preventDefault();void load(from,through)}}>
    <Field label="From"><Input type="date" required value={from} onChange={(_,d)=>setFrom(d.value)}/></Field>
    <Field label="Through"><Input type="date" required min={from} value={through} onChange={(_,d)=>setThrough(d.value)}/></Field>
    <Button type="submit" appearance="primary" disabled={busy}>Update reports</Button></form>
    {busy&&<Spinner label="Loading reports"/>}{error&&<p className="error" role="alert">{error}</p>}
    {!busy&&report&&<><p className="muted">{report.scope} · {shown} · Refreshed {new Date(report.refreshedAt).toLocaleTimeString()}</p>
    <div className="cards">{report.metrics.map(m=><section className="card" key={m.name}><h2>{m.name}</h2><strong>
      {m.value==null?'—':m.value.toLocaleString(undefined,{maximumFractionDigits:2})} {m.value!=null&&<small>{m.unit}</small>}</strong>
      <Badge appearance="tint" color={m.status==='AVAILABLE'?'success':m.status==='SUPPRESSED'?'warning':'informative'}>{m.status.replaceAll('_',' ').toLowerCase()}</Badge>
      <p>{m.formula}</p><small className="muted">{m.coverage}</small>{m.denominator!=null&&<div className="muted">Denominator {Number(m.denominator.toFixed(2))}</div>}
    </section>)}</div>
    {!!Object.keys(report.departments).length&&<Panel title="Workforce distribution">{Object.entries(report.departments).map(([key,num])=>
      <div className="bar-row" key={key}><span>{key}</span><div className="bar" aria-hidden="true"
        style={{width:(100*num/Math.max(1,...Object.values(report.departments)))+'%'}}/><strong>{num}</strong></div>)}</Panel>}
    {!!report.rejectionReasons.length&&<Panel title="Rejection reasons"><Table rows={report.rejectionReasons} columns={[[ 'reason','Reason' ],[ 'count','Count' ]]}/></Panel>}
    {!!report.tenureFlags.length&&<Panel title="Tenure review"><Table rows={report.tenureFlags} columns={[[ 'employeeId','Employee' ],
      [ 'name','Name' ],[ 'completedYears','Years' ]]}/></Panel>}
    {!!report.payCohorts.length&&<Panel title="Comparable pay groups">{report.payCohorts.map(({cohort,groups},i)=><div key={i}>
      <p>{Object.values(cohort).join(' · ')}</p><Table rows={groups} columns={[[ 'category','Category' ],[ 'sampleSize','Employees' ],[ 'mean','Mean base pay' ]]}/></div>)}</Panel>}
    </>}</>
}
