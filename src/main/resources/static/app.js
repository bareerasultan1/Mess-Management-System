'use strict';
const byId = id => document.getElementById(id);
const el = (tag, text) => Object.assign(document.createElement(tag), {textContent:text ?? ''});
const values = form => Object.fromEntries(new FormData(form));
let session, staff, student, admin;
const pages = {menu:0,inbox:0,cases:0};
function say(message, error=false) {
  const box=byId('notice'); box.textContent=message; box.classList.toggle('error',error); box.focus();
}
async function api(path, method='GET', data) {
  const headers={Accept:'application/json'};
  if(data !== undefined) headers['Content-Type']='application/json';
  if(session) headers['X-CSRF-TOKEN']=session.csrf;
  const response=await fetch('/api/'+path,{method,headers,body:data===undefined?undefined:JSON.stringify(data)});
  const raw=await response.text();
  let result; try {result=raw?JSON.parse(raw):null;} catch {result=null;}
  if(!response.ok) throw Error(result?.message || (response.status===403?'Access denied. Sign in again if your session expired.':'Request failed ('+response.status+').'));
  return result;
}
function action(message, job) {
  return async event => {
    event?.preventDefault();
    const control=event?.submitter || event?.currentTarget;
    if(control instanceof HTMLButtonElement) control.disabled=true;
    try {await job(); if(message) say(message);}
    catch(err){say(err.message,true);}
    finally {if(control instanceof HTMLButtonElement) control.disabled=false;}
  };
}
function button(text,job,message) {
  const b=el('button',text); b.type='button'; b.addEventListener('click',action(message,job)); return b;
}
function field(title,control) {const label=el('label',title);label.append(control);return label;}
function form(id,message,job) {
  const f=byId(id); f.addEventListener('submit',action(message,()=>job(values(f),f)));
}
function list(id,rows,key,load,render,empty) {
  const box=byId(id);box.replaceChildren();
  if(!rows.length) box.append(el('p',empty));
  for(const row of rows) box.append(render(row));
  const nav=el('nav');nav.setAttribute('aria-label',key+' pages');
  const pageBy=async step=>{pages[key]+=step;try {await load();}catch(err){pages[key]-=step;throw err;}};
  const prev=button('Previous',()=>pageBy(-1)),next=button('Next',()=>pageBy(1));
  prev.disabled=pages[key]===0;next.disabled=rows.length<20;
  nav.append(prev,el('span','Page '+(pages[key]+1)),next);box.append(nav);
}
async function menuList() {
  const query=new URLSearchParams(values(byId('filter')));
  for(const key of ['day','meal']) if(!query.get(key)) query.delete(key);
  query.set('page',pages.menu);
  const rows=await api((staff?'staff':'student')+'/menus?'+query);
  list('menuList',rows,'menu',menuList,m=>{
    const card=el('article');
    card.append(el('strong',m.day+' · '+m.meal+(m.special?' · Special meal':'')),el('p',m.dishes));
    if(staff) {
      card.append(el('p',(m.published?'Published':'Draft')+' · Version '+m.version));
      const edit=el('form'), dishes=el('input'), special=el('input');
      dishes.required=true;dishes.maxLength=500;dishes.value=m.dishes;
      special.type='checkbox';special.checked=m.special;
      edit.append(field('Dishes',dishes),field('Special meal',special),el('button','Save changes'));
      edit.onsubmit=action('Menu updated.',async()=>{await api('staff/menus/'+m.id,'PUT',{version:m.version,dishes:dishes.value,special:special.checked});await menuList();});
      card.append(edit);
      if(!m.published) card.append(button('Publish',async()=>{
        const r=await api('staff/menus/'+m.id+'/publish','POST',{version:m.version});
        byId('replay').elements.id.value=r.event;
        await menuList();say('Published. Inbox delivery queued. Event '+r.event);
      }));
    } else {
      const score=el('select');for(let i=1;i<=5;i++) score.append(el('option',String(i)));
      score.value='3';
      card.append(field('Meal rating (1–5)',score),button('Submit rating',()=>api('student/ratings','POST',{menu:m.id,score:Number(score.value)}),'Anonymous rating saved.'));
    }
    return card;
  },'No menus match this date and meal.');
}
async function inboxList() {
  const rows=await api('student/inbox?page='+pages.inbox);
  list('inboxList',rows,'inbox',inboxList,r=>{
    const card=el('article');card.append(el('strong',r.kind),el('p',r.summary),el('small',new Date(r.delivered).toLocaleString()));return card;
  },'Your inbox is empty.');
}
async function casesList() {
  const rows=await api((staff?'staff/cases':'admin/escalations')+'?page='+pages.cases);
  list('caseList',rows,'cases',casesList,r=>{
    const card=el('article');card.append(el('strong',r.kind+' · '+r.status+(r.escalated?' · Escalated':'')),el('p',r.body),el('small',r.ref));
    const next={OPEN:'IN_REVIEW',IN_REVIEW:'RESOLVED',RESOLVED:'CLOSED'}[r.status];
    if(staff && next) card.append(button('Mark '+next,async()=>{await api('staff/cases/'+r.ref,'PATCH',{from:r.status,to:next});await casesList();},'Status updated to '+next+'.'));
    return card;
  },admin?'No escalations to review.':'No feedback or complaints yet.');
}
async function start() {
  session=await api('session');
  staff=session.roles.includes('ROLE_STAFF');student=session.roles.includes('ROLE_STUDENT');admin=session.roles.includes('ROLE_ADMIN');
  const account=byId('account');
  if(session.name) {
    const signout=el('form');signout.method='post';signout.action='/logout';
    const token=el('input');token.type='hidden';token.name='_csrf';token.value=session.csrf;
    signout.append(el('span',session.name),token,el('button','Sign out'));account.append(signout);
  } else {const a=el('a','Sign in');a.href='/login';account.append(a);}
  for(const [id,show] of Object.entries({menus:student||staff,draft:staff,alerts:student,submitPanel:student,queue:admin||staff,fixtures:staff&&session.demo})) byId(id).hidden=!show;
  if(admin) byId('caseTitle').textContent='Escalated complaints';
  form('filter',null,async()=>{pages.menu=0;await menuList();});
  form('create','Draft saved.',async(v,f)=>{await api('staff/menus','POST',{...v,special:f.elements.special.checked});f.reset();byId('filter').reset();pages.menu=0;await menuList();});
  form('submitCase','Submission received. Save your tracking reference.',async(v,f)=>{
    const r=await api('student/feedback','POST',v);f.reset();byId('receipt').textContent='Private reference: '+r.ref;byId('track').elements.ref.value=r.ref;
  });
  form('track',null,async v=>{byId('progress').textContent='';const r=await api('track','POST',v);byId('progress').textContent=r.status+(r.escalated?' · Escalated':'');say('Tracking status updated.');});
  form('fixture','Fixture queued.',async v=>{const r=await api('staff/fixtures','POST',v);byId('replay').elements.id.value=r.event;});
  form('replay','Replay queued. Refresh the student inbox to check the result.',v=>api('staff/fixtures/'+encodeURIComponent(v.id)+'/replay','POST'));
  byId('refreshInbox').onclick=action('Inbox refreshed.',inboxList);byId('refreshCases').onclick=action('Case list refreshed.',casesList);
  if(student||staff) await menuList();if(student) await inboxList();if(staff||admin) await casesList();
  byId('notice').textContent=session.name?'Signed in as '+session.name+'.':'Sign in to use your desk, or track a submission below.';
}
start().catch(err=>say(err.message,true));
