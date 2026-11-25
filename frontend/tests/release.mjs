import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'
import { mkdtemp, readFile, rmdir, unlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const base = process.env.HRIS_TEST_URL ?? 'http://127.0.0.1:18087'
const password = process.env.HRIS_DEMO_PASSWORD
if (!password) throw new Error('Set HRIS_DEMO_PASSWORD to the isolated test database password')
const browser = await chromium.launch({
  executablePath: process.env.HRIS_EDGE_PATH ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  headless: true,
})
const context = await browser.newContext({ viewport: { width: 1366, height: 900 } })
const page = await context.newPage()
const fixtures = await mkdtemp(join(tmpdir(), 'tc-browser-'))
const csvFile = join(fixtures, 'browser-fixture.csv')
const errors = []
page.on('pageerror', error => errors.push(error.message))
page.on('requestfailed', request => {
  if (request.failure()?.errorText !== 'net::ERR_ABORTED') errors.push(request.url() + ' ' + request.failure()?.errorText)
})
page.on('response', response => {
  const path = new URL(response.url()).pathname
  if (path.startsWith('/api/') && response.status() >= 400
      && !(path === '/api/v1/session' && response.status() === 401)) {
    errors.push(path + ' HTTP ' + response.status())
  }
})
page.on('console', message => {
  if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
    errors.push(message.text())
  }
})
page.setDefaultTimeout(10_000)

const nav = name => page.getByRole('navigation').getByRole('button', { name, exact: true })
const button = name => page.getByRole('button', { name, exact: true })
const field = name => page.getByLabel(name, { exact: true }).or(page.locator('[aria-label=' + JSON.stringify(name) + ']'))
async function fill(fields) {
  for (const [label, value] of Object.entries(fields)) await field(label).fill(value)
}
async function heading(name) {
  await page.getByRole('heading', { name, exact: true }).first().waitFor()
}
async function sign(org, username) {
  await heading('Sign in')
  await fill({ Organization: org, Username: username, Password: password })
  await button('Sign in').click()
  await heading('Overview')
}
async function report(from, through) {
  await fill({ From: from, Through: through })
  await button('Update reports').click()
  await page.getByText(from + ' to ' + through, { exact: false }).waitFor()
}
async function choose(name) {
  await nav(name).click()
  await heading(name)
}
async function card(name, expected) {
  const metric = page.locator('.card').filter({ has: page.getByRole('heading', { name, exact: true }) })
  assert.match(await metric.innerText(), expected)
}
async function metricInput(name, values, picks = {}) {
  await field('Input type').selectOption(name)
  await fill(values)
  for (const [label, value] of Object.entries(picks)) await field(label).selectOption(value)
  await button('Save input').click()
  await page.getByRole('status').filter({ hasText: 'Saved.' }).waitFor()
}
try {
  await page.goto(base + '/')
  await heading('Sign in')
  await page.keyboard.press('Tab')
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-label')), 'Organization')
  await page.keyboard.press('Tab')
  assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('aria-label')), 'Username')
  await button('Sign in').click()
  await page.getByText('Complete this field.', { exact: true }).waitFor()
  await sign('DEMO-A', 'a-admin')
  await report('2025-01-01', '2025-01-31')
  await heading('Headcount')

  await choose('Employees')
  await button('New employee').click()
  await fill({ 'Employee number': 'UI-EMP', 'Full name': 'Browser Employee', 'Job title': 'Analyst',
    'Effective date': '2025-04-04', 'Work location': 'Campus', 'Employment type': 'Part time' })
  await field('Department').first().selectOption('OPS')
  await page.getByRole('button', { name: 'Create employee', exact: true }).click()
  await page.getByRole('cell', { name: 'Browser Employee' }).waitFor()
  await page.getByRole('row', { name: /Browser Employee/ }).getByRole('button', { name: 'Edit' }).click()
  await fill({ 'Full name': 'Browser Employee Updated' })
  await button('Save changes').click()
  await page.getByRole('cell', { name: 'Browser Employee Updated' }).waitFor()
  await page.getByRole('row', { name: /Browser Employee Updated.*Campus.*Part time/ }).waitFor()
  await page.reload()
  await heading('Overview')
  await choose('Employees')
  await page.getByRole('cell', { name: 'Browser Employee Updated' }).waitFor()

  await choose('Recruitment')
  await button('Open requisition').click()
  await fill({ 'Requisition code': 'UI-REQ', 'Job title': 'Designer', 'Opened on': '2025-04-01' })
  await field('Department').selectOption('ENG')
  await button('Save requisition').click()
  await page.getByRole('cell', { name: 'UI-REQ', exact: true }).waitFor()
  await button('Close form').click()
  await button('Link candidate').click()
  await fill({ 'Application code': 'UI-APP', 'Requisition code': 'UI-REQ', 'Candidate code': 'UI-CAND',
    'Candidate name': 'Browser Candidate', 'Applied on': '2025-04-02' })
  await button('Save application').click()
  await button('Close form').click()
  for (const [stage, date] of [['INTERVIEW','2025-04-03'],['OFFERED','2025-04-04'],['ACCEPTED','2025-04-05'],['HIRED','2025-04-06']]) {
    await page.getByRole('row', { name: /UI-APP .*Browser Candidate/ }).getByRole('button', { name: 'Update stage' }).click()
    await field('Next stage').selectOption(stage)
    await field('Stage date').fill(date)
    if (stage==='HIRED') await field('Employee number for hire').fill('UI-HIRE')
    await button('Record stage').click()
    await page.getByRole('row', { name: /UI-APP .*Browser Candidate/ }).getByRole('cell', { name: stage, exact: true }).waitFor()
  }
  await choose('Overview')
  await report('2025-04-01', '2025-04-30')
  await card('Time to fill', /4 days/)
  await card('Time to hire', /3 days/)
  await choose('Metric inputs')
  await metricInput('Finance', { From: '2025-04-01', Through: '2025-04-30', Currency: 'USD',
    'Recruiting costs': '1000', 'Employee costs': '9000', Revenue: '20000' })
  await metricInput('Hire outcomes', { 'Application code': 'UI-APP', 'Measured on': '2025-04-25',
    'Performance score': '90', 'Manager score': '60' }, { Retained: 'Yes' })
  await metricInput('Attendance', { 'Employee number': 'UI-HIRE', 'Scheduled date': '2025-04-06',
    'Scheduled hours': '8', 'Unplanned absence hours': '0' })
  await metricInput('Surveys', { 'Survey code': 'UI-PULSE', Version: '1', Question: 'How engaged do you feel?' },
    { Department: 'OPS', 'Survey category': 'ENGAGEMENT' })
  await metricInput('Survey scores', { 'Survey code': 'UI-PULSE', Version: '1', 'Response date': '2025-04-12',
    'Anonymous scores from 1 to 5, separated by commas': '3,4,5' })
  await choose('Overview')
  await report('2025-04-01', '2025-04-30')
  await card('Cost per hire', /1,?000 USD/)
  await card('Quality of hire', /85 \/100/)
  await card('Absenteeism', /0 %/)
  await card('Engagement', /4 \/5/)

  await choose('CSV import')
  const header='ID,FullName,Gender,Department,Role,JoiningDate,BasicSalary,Status'
  const good='UI-CSV,"Browser, CSV",F,Operations,Analyst,2025-04-01,5000,ACTIVE'
  const original=header+'\n'+good+'\nBAD,Test,Other,Operations,Analyst,invalid,2000,ACTIVE'
  await writeFile(csvFile, original)
  await field('CSV file').setInputFiles(csvFile)
  await field('Legacy currency').fill('USD')
  await field('Legacy FTE').fill('1')
  await button('Preview CSV').click()
  await page.getByRole('cell', { name: 'Joining date must be a valid ISO date' }).waitFor()
  await field('CSV contents').fill(header+'\n'+good)
  await button('Preview CSV').click()
  await button('Commit validated records').click()
  await page.getByText('Committed rows', { exact: false }).waitFor()
  assert.equal(await readFile(csvFile, 'utf8'), original)

  await choose('Administration')
  await page.getByRole('cell', { name: 'PROFILE_EDITED' }).first().waitFor()
  await page.setViewportSize({width:768,height:1024})
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await button('Sign out').click()
  await sign('DEMO-A', 'a-manager')
  await choose('Employees')
  await page.getByRole('cell', { name: 'Browser Candidate' }).waitFor()
  assert.equal(await button('New employee').count(), 0)
  assert.equal(await page.getByRole('cell', { name: 'Browser Employee Updated' }).count(), 0)

  await button('Sign out').click()
  await sign('DEMO-A', 'a-finance')
  assert.equal(await nav('Employees').count(), 0)
  await choose('Compensation')
  await page.getByRole('heading', { name: 'Base pay history' }).waitFor()
  assert.equal(await button('Save base pay').count(), 0)

  await button('Sign out').click()
  await sign('DEMO-B', 'b-admin')
  await choose('Employees')
  await page.getByRole('cell', { name: 'Synthetic B Engineer' }).waitFor()
  assert.equal(await page.getByRole('cell', { name: 'Synthetic A Engineer' }).count(), 0)
  assert.deepEqual(errors,[])
  console.log('Edge browser workflows passed for HR Admin, Manager, Finance and the second organization at desktop and tablet widths.')
} finally {
  await browser.close()
  await unlink(csvFile).catch(error => { if (error.code !== 'ENOENT') throw error })
  await rmdir(fixtures)
}
