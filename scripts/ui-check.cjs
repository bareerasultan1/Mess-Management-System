// Browser-only interaction test. Network responses are explicit fixtures.
const { chromium } = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const root = 'src/main/resources/static/';
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN || undefined});
  try {
    for(const role of ['staff','student','admin','public']){
      const page=await browser.newPage({viewport:{width:390,height:844}});
      const errors=[];const writes=[];let stale=false;
      page.on('pageerror',error=>errors.push(error.message));
      const menu={id:'meal',day:'2026-11-19',meal:'DINNER',dishes:'Daal',special:false,published:role==='student',version:0};
      await page.route('http://campus.test/**',async route=>{
        const url=new URL(route.request().url());
        const path=url.pathname;
        if(!path.startsWith('/api/')){
          const file=path==='/'?'index.html':path.slice(1);
          return route.fulfill({body:fs.readFileSync(root+file),contentType:({'index.html':'text/html','app.js':'text/javascript','campus.css':'text/css'})[file]});
        }
        let data=[];
        if(stale && route.request().method()==='PUT') return route.fulfill({status:409,json:{message:'Menu changed. Refresh and retry.'}});
        if(path==='/api/session') data={name:role==='public'?'':role,roles:['ROLE_'+role.toUpperCase()],csrf:'fixture',demo:true};
        else if(route.request().method()!=='GET'){
          assert.equal(route.request().headers()['x-csrf-token'],'fixture'); writes.push(path);
          if(path.endsWith('/publish')) {menu.published=true;menu.version++;data={event:'replay-id'};}
          if(path.endsWith('/feedback')) data={ref:'private-tracking-id'};
          if(path==='/api/track') data={status:'OPEN',escalated:false};
        } else if(path.endsWith('/menus')) data=[menu];
        await route.fulfill({json:data});
      });
      await page.goto('http://campus.test/');
      await page.getByText(role==='public'?'Sign in to use your desk, or track a submission below.':'Signed in as '+role+'.',{exact:true}).waitFor();
      if(role==='staff'){
        await page.getByRole('button',{name:'Publish',exact:true}).focus();
        await page.keyboard.press('Enter');
        await page.getByText('Published. Inbox delivery queued.',{exact:false}).waitFor();
        assert(writes.includes('/api/staff/menus/meal/publish'));
        assert.equal(await page.locator('#replay input').inputValue(),'replay-id');
        stale=true;await page.getByRole('button',{name:'Save changes'}).click();
        await page.getByText('Menu changed. Refresh and retry.').waitFor();
        assert.equal(await page.locator('#notice').evaluate(e=>e===document.activeElement),true);
      } else if(role==='student'){
        await page.getByLabel('Message',{exact:true}).fill('fixture');
        await page.getByRole('button',{name:'Submit anonymously'}).click();
        await page.getByText('Private reference: private-tracking-id').waitFor();
        await page.getByRole('button',{name:'Check status'}).click();
        await page.getByText('Tracking status updated.').waitFor();
        assert.equal(await page.locator('#progress').textContent(),'OPEN');
        await page.getByRole('button',{name:'Submit rating'}).click();
        await page.getByText('Anonymous rating saved.').waitFor();
        await page.getByText('Your inbox is empty.').waitFor();
      } else if(role==='admin') {
        await page.getByText('No escalations to review.').waitFor();
        assert.equal(await page.locator('#draft').isVisible(),false);
      } else {
        await page.getByRole('link',{name:'Sign in'}).waitFor();
      }
      assert.deepEqual(errors,[]);
      console.log('PASS '+role+' UI fixture at 390px; no page errors');
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});
