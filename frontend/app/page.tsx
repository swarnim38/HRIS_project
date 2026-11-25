'use client'
import { Badge,Button,FluentProvider,Spinner,webLightTheme } from '@fluentui/react-components'
import { useEffect,useState } from 'react'
import { Editor,type Call,type Context,type Role } from './shared'
import { Employees,Recruitment,Compensation } from './workflows'
import { Inputs,Imports,Administration } from './inputs'
import { Overview } from './overview'
type Session={organization:string;username:string;role:Role;departmentId?:string;csrf:string}
const roles={HR_ADMIN:'HR Admin',MANAGER:'Manager',FINANCE:'Finance'}
export default function App(){
  const [session,setSession]=useState<Session|null>(null)
  const [loading,setLoading]=useState(true)
  const [error,setError]=useState('')
  const [page,setPage]=useState('Overview')
  const [revision,setRevision]=useState(0)
  const [departments,setDepartments]=useState<string[]>([])
  const call:Call=async(path,method='GET',body)=>{
    const response=await fetch('/api/v1'+path,{method,cache:'no-store',
      headers:{...(body?{'Content-Type':'application/json'}:{}),...(session?{'X-CSRF-Token':session.csrf}:{})},
      body:body?JSON.stringify(body):undefined})
    if(!response.ok){
      const problem=await response.json().catch(()=>null)
      if(response.status===401&&path!=='/login'&&path!=='/session')setSession(null)
      throw new Error(problem?.detail??('Request failed ('+response.status+').'))
    }
    const value=await response.text()
    return value?JSON.parse(value):undefined
  }
  useEffect(()=>{
    fetch('/api/v1/session',{cache:'no-store'}).then(r=>{
      if(r.status===401)return null
      if(!r.ok)throw new Error('Could not connect to the backend.')
      return r.json()
    }).then(setSession).catch(e=>setError(e.message)).finally(()=>setLoading(false))
  },[])
  useEffect(()=>{
    let active=true
    if(session&&session.role!=='FINANCE')call<{id:string}[]>('/departments')
      .then(rows=>{if(active)setDepartments(rows.map(d=>d.id))})
      .catch(e=>{if(active){setError(e.message);setDepartments([])}})
    return ()=>{active=false}
  },[session,revision])
  const brand=<div className="brand"><span className="mark" aria-hidden="true">✦</span>TalentCompass</div>
  if(loading)return <FluentProvider theme={webLightTheme}><main className="login"><Spinner label="Loading workspace"/></main></FluentProvider>
  if(!session)return <FluentProvider theme={webLightTheme}><main className="login">{brand}<section className="panel"><h1>Sign in</h1>
    {error&&<p className="error" role="alert">{error}</p>}<Editor submit="Sign in"
      fields={[[ 'organization','Organization' ],[ 'username','Username' ],[ 'password','Password','password' ]]}
      onSave={async data=>{setSession(await call<Session>('/login','POST',data));setError('');setPage('Overview')}}/></section></main></FluentProvider>
  const ctx:Context={call,revision,departments,role:session.role,username:session.username,changed:()=>setRevision(n=>n+1)}
  const pages=session.role==='FINANCE'?['Overview','Compensation','Metric inputs']:['Overview','Employees','Recruitment','Compensation',
    ...(session.role==='HR_ADMIN'?['Metric inputs','CSV import','Administration']:[])]
  return <FluentProvider theme={webLightTheme}><a className="skip" href="#main">Skip to content</a><header>{brand}
    <div className="tools"><span>{session.username}</span><Badge appearance="tint">{roles[session.role]}</Badge><Button appearance="subtle"
        onClick={async()=>{try{await call('/logout','POST');setSession(null);setError('');setDepartments([])}catch(e){setError((e as Error).message)}}}>Sign out</Button></div>
    </header><div className="shell"><aside><p>{session.organization}{session.departmentId?' / '+session.departmentId:''}</p>
      <nav aria-label="Workspace">{pages.map(item=><button key={item} aria-current={item===page?'page':undefined} onClick={()=>setPage(item)}>{item}</button>)}</nav>
    </aside><main className="content" id="main" tabIndex={-1}><h1>{page}</h1>{error&&<p className="error" role="alert">{error}</p>}
      {page==='Overview'&&<Overview ctx={ctx}/>}
      {page==='Employees'&&<Employees ctx={ctx}/>}
      {page==='Recruitment'&&<Recruitment ctx={ctx}/>}
      {page==='Compensation'&&<Compensation ctx={ctx}/>}
      {page==='Metric inputs'&&<Inputs ctx={ctx}/>}
      {page==='CSV import'&&<Imports ctx={ctx}/>}
      {page==='Administration'&&<Administration ctx={ctx}/>}
    </main></div></FluentProvider>
}
