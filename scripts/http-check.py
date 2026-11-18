"""Live demo smoke and small HTTP load run. Requires running demo on loopback.

Set MESS_DEMO_PASSWORD to the password used by the demo. Creates a menu and alerts.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
import http.cookiejar
from urllib.error import URLError
import json
import math
import os
import secrets
import threading
import time
from urllib.parse import urlencode
from urllib.request import build_opener, HTTPCookieProcessor, Request

parser=argparse.ArgumentParser()
parser.add_argument("--clients",type=int,default=8)
parser.add_argument("--reads",type=int,default=20)
args=parser.parse_args()
if not 1<=args.clients<=500 or not 1<=args.reads<=1000: parser.error("Clients 1..500, reads 1..1000")
base="http://127.0.0.1:8080"
class Session:
    def __init__(self,name):
        self.http=build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.token=self.call("/api/session")["csrf"]
        body=urlencode(dict(username=name,password=os.environ["MESS_DEMO_PASSWORD"],_csrf=self.token)).encode()
        with self.http.open(base+"/login",body,timeout=10): pass
        state=self.call("/api/session")
        if state["name"]!=name or not state["demo"]: raise RuntimeError("Demo sign-in failed")
        self.token=state["csrf"]
    def call(self,path,body=None,method=None):
        data=None if body is None else json.dumps(body).encode()
        headers={"Content-Type":"application/json","X-CSRF-TOKEN":getattr(self,"token","")}
        with self.http.open(Request(base+path,data,headers,method=method),timeout=10) as response:
            raw=response.read()
            return json.loads(raw) if raw else None

def wait_for(probe,seconds=60):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=probe()
        if value: return value
        threading.Event().wait(0.05)
    raise TimeoutError("Condition did not become true before deadline")

staff,student=Session("staff"),Session("student")
for _ in range(20):
    day=str(date(2100,1,1)+timedelta(days=secrets.randbelow(20000)))
    if not staff.call("/api/staff/menus?day="+day+"&meal=LUNCH"): break
else: raise RuntimeError("No empty demo slot selected")
record=staff.call("/api/staff/menus",dict(day=day,meal="LUNCH",dishes="Demo chana pulao",special=False))
assert not student.call("/api/student/menus?day="+day)
start=time.monotonic()
event=staff.call("/api/staff/menus/"+record["id"]+"/publish",{"version":0})["event"]
assert student.call("/api/student/menus?day="+day)[0]["id"]==record["id"]
def matches(): return [i for i in student.call("/api/student/inbox") if i["id"]==event]
wait_for(matches)
latency=time.monotonic()-start
def done(): return staff.call("/api/staff/fixtures/"+event)["state"]=="DONE"
wait_for(done)
staff.call("/api/staff/fixtures/"+event+"/replay",{},method="POST")
wait_for(done)
assert len(matches())==1
print(f"PASS menu publish -> visible -> inbox -> replay; event={event}; observed alert latency={latency:.3f}s")
# One authenticated session per client, all reading the student fixture's inbox.
sessions=[Session("student") for _ in range(args.clients)]
gate=threading.Barrier(args.clients+1)
def load(session):
    samples=[]; failures=0
    gate.wait(timeout=30)
    for _ in range(args.reads):
        tick=time.perf_counter()
        try: session.call("/api/student/inbox")
        except (URLError, TimeoutError, OSError, ValueError): failures+=1
        samples.append((time.perf_counter()-tick)*1000)
    return samples,failures
with ThreadPoolExecutor(max_workers=args.clients) as pool:
    jobs=[pool.submit(load,s) for s in sessions]
    before=time.perf_counter(); gate.wait(timeout=30)
    results=[job.result() for job in jobs]
    elapsed=time.perf_counter()-before
times=sorted(t for batch,_ in results for t in batch)
errors=sum(n for _,n in results)
print(f"clients={args.clients} attempts={len(times)} completed/s={len(times)/elapsed:.2f} p95_ms={times[math.ceil(.95*len(times))-1]:.2f} errors={errors}")
if errors: raise SystemExit(1)
