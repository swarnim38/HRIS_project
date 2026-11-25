'use client'
import { Button, Field, Input, Select, Spinner, Textarea } from '@fluentui/react-components'
import { useEffect, useId, useState, type ReactNode } from 'react'

export type Values=Record<string,string|number|number[]|boolean|null>
export type Row=Record<string,unknown>
export type Spec=[key:string,label:string,type?:string,choices?:string[]]
export type Column=[key:string,label:string]
export type Call=<T>(path:string,method?:string,body?:Values)=>Promise<T>
export type Role='HR_ADMIN'|'MANAGER'|'FINANCE'
export type Context={call:Call;revision:number;changed:()=>void;departments:string[];role:Role;username:string}
export const day=()=>new Date().toLocaleDateString('en-CA',{year:'numeric',month:'2-digit',day:'2-digit'})
export const endpoint=(path:string,id:unknown,suffix='')=>path+'/'+encodeURIComponent(String(id))+suffix
export const department=(options:string[],optional=false):Spec=>['departmentId',optional?'Department?':'Department','select',options]
export const save=(ctx:Context,path:string,method='POST')=>async(data:Values)=>{await ctx.call(path,method,data);ctx.changed()}

export function Panel({title,children}:{title:string;children:ReactNode}){
 return <section className="panel"><h2>{title}</h2>{children}</section>
}
export function Editor({fields,initial={},submit='Save',onSave,notice='Saved.'}:{
 fields:Spec[];initial?:Values;submit?:string;onSave:(data:Values)=>Promise<void>;notice?:string
}){
 const prefix=useId()
 const [values,setValues]=useState(initial)
 const [error,setError]=useState('')
 const [invalid,setInvalid]=useState('')
 const [done,setDone]=useState(false)
 const [busy,setBusy]=useState(false)
 return <form noValidate onSubmit={async e=>{
   e.preventDefault();setError('');setInvalid('');setDone(false)
   const payload:Values={}
   for(const [key,label,kind] of fields){
     const raw=String(values[key]??'')
     const value=kind==='password'?raw:raw.trim()
     if(!value&&!label.endsWith('?')){setInvalid(key);setError('Complete this field.');document.getElementById(prefix+key)?.focus();return}
     payload[key]=!value?null:kind==='number'?Number(value):kind==='scores'?value.split(/[\s,]+/).map(Number):value
   }
   setBusy(true)
   try {await onSave(payload);setDone(true)}
   catch(error){const message=(error as Error).message;setError(message);setInvalid(fields.find(([,label])=>message.toLowerCase().includes(label.toLowerCase()))?.[0]??'')}
   finally{setBusy(false)}
 }}><div className="fields">{fields.map(([key,label,type,choices])=>{
   const change=(v:string)=>{setValues({...values,[key]:v});setDone(false);if(invalid===key){setError('');setInvalid('')}}
   const value=String(values[key]??'')
   return <Field key={key} label={label.replace(/\?$/,'')} required={!label.endsWith('?')} validationMessage={invalid===key?error:undefined}>
     {type==='select'?<Select id={prefix+key} aria-label={label.replace(/\?$/,'')} value={value} onChange={e=>change(e.target.value)}><option value="">Select</option>
       {choices?.map(choice=><option key={choice}>{choice}</option>)}</Select>:type==='scores'?
       <Textarea id={prefix+key} aria-label={label.replace(/\?$/,'')} value={value} onChange={(_,d)=>change(d.value)}/>:
       <Input id={prefix+key} aria-label={label.replace(/\?$/,'')} type={(type??'text') as 'text'|'date'|'number'|'password'} step={type==='number'?'any':undefined}
         value={value} onChange={(_,d)=>change(d.value)}/>}
   </Field>
 })}</div>{error&&!invalid&&<p className="error" role="alert">{error}</p>}{done&&<p className="success" role="status">{notice}</p>}
 <Button type="submit" appearance="primary" disabled={busy}>{busy?'Saving…':submit}</Button></form>
}

export function Table({rows,columns,action}:{rows:Row[];columns:Column[];action?:(row:Row)=>ReactNode}){
 if(!rows.length)return <p className="muted">No records to show.</p>
 return <div className="table-wrap"><table><thead><tr>{columns.map(([key,label])=><th key={key} scope="col">{label}</th>)}
   {action&&<th scope="col">Actions</th>}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{columns.map(([key])=>
   <td key={key}>{row[key]==null?'—':String(row[key])}</td>)}{action&&<td>{action(row)}</td>}</tr>)}</tbody></table></div>
}
export function useRows(call:Call,path:string,revision:number){
 const [rows,setRows]=useState<Row[]>([])
 const [error,setError]=useState('')
 const [busy,setBusy]=useState(true)
 useEffect(()=>{let active=true;setBusy(true);setError('')
   call<Row[]>(path).then(data=>{if(active)setRows(data??[])}).catch(e=>{if(active){setRows([]);setError(e.message)}})
   .finally(()=>{if(active)setBusy(false)})
   return ()=>{active=false}
 },[path,revision])
 return {rows,error,busy}
}
export function Records({ctx,path,columns,action}:{ctx:Context;path:string;columns:Column[];action?:(row:Row)=>ReactNode}){
 const result=useRows(ctx.call,path,ctx.revision)
 return result.busy?<Spinner size="tiny" label="Loading records"/>:result.error?<p role="alert" className="error">{result.error}</p>
   :<Table rows={result.rows} columns={columns} action={action}/>
}
