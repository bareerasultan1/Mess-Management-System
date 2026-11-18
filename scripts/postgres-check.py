"""Run shared production SQL in a fresh schema on an explicitly chosen test database.

Use --standalone-data only with a stopped, disposable PostgreSQL cluster.
No production schema is truncated. Two backend sessions also exercise restart recovery.
"""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import uuid

from lock_checks import check_held_locks

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
parser = argparse.ArgumentParser()
mode = parser.add_mutually_exclusive_group(required=True)
mode.add_argument("--standalone-data", type=Path)
mode.add_argument("--database", help="Test database name for psql; PGHOST/PGPORT/PGUSER select connection")
parser.add_argument("--pg-bin", type=Path)
parser.add_argument("--held-locks", action="store_true", help="Run the held-transaction suite in a stopped disposable cluster")
args = parser.parse_args()
if args.held_locks and not args.standalone_data:
    parser.error("--held-locks requires --standalone-data")
schema = "m2check_" + uuid.uuid4().hex
menu, event, rolled, complaint, closed, feedback = (str(uuid.uuid4()) for _ in range(6))
now = "2026-11-18 00:00:00+00"
projection = "select status, escalated is not null as escalated,created,resolved from campus_case"

def literal(v):
    if v is None: return "NULL"
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, int): return str(v)
    return "'" + v.replace("'", "''") + "'"

def sql(name, *vals):
    src = (RES / "sql" / (name + ".sql")).read_text().strip()
    parts = src.split("?")
    assert len(vals) + 1 == len(parts), name
    return parts[0] + "".join(literal(v) + part for v, part in zip(vals, parts[1:]))

def run(statements):
    name = "postgres" if args.standalone_data else "psql"
    exe = str(args.pg_bin / name) if args.pg_bin else shutil.which(name)
    if not exe: raise SystemExit(name+" missing from PATH")
    if args.standalone_data:
        cmd = [exe,"--single","-D",str(args.standalone_data.resolve()),"-j"]
        if args.held_locks: cmd.extend(["-c","max_prepared_transactions=8"])
        cmd.append("postgres")
    else:
        cmd = [exe,"-X","-tA","-v","ON_ERROR_STOP=1","-d",args.database]
    proc = subprocess.run(cmd,input="\n\n".join(s.strip().rstrip(";")+";" for s in statements)+"\n\n",
                          capture_output=True,text=True,timeout=120,check=False)
    output = proc.stdout + proc.stderr
    if proc.returncode or re.search(r"\b(ERROR|FATAL|PANIC):",output):
        raise RuntimeError(output[-9000:])
    return output

def expect(test, label):
    return f"do $$ begin if not ({test}) then raise exception 'failed {label}'; end if; end $$"

def insert_event(eid):
    return sql("insert_event",eid,now,"MENU",None,"Lunch published",now)

path = f"set search_path to {schema}"
creation = False
try:
    run([f"create schema {schema}"])
    creation = True
    run([path,(RES/"schema.sql").read_text(),(RES/"demo-students.sql").read_text()])
    if args.held_locks:
        check_held_locks(run, sql, expect, schema)
        print("PASS all five PostgreSQL held-transaction scenarios; no TCP or Spring involved")
        raise SystemExit(0)
    run([path,
         f"insert into campus_menu(id,day,meal,dishes,special) values('{menu}','2026-11-18','LUNCH','Daal',false)",
         expect("not exists(select from campus_menu where published)","draft visibility"),
         "begin",sql("publish_menu",menu,0),insert_event(rolled),"rollback",
         expect("not exists(select from campus_event) and not exists(select from campus_menu where published)","rollback"),
         "begin",sql("publish_menu",menu,0),insert_event(event),"commit"])
    print("PASS draft hidden; menu and event rolled back together; publication committed")
    # The first connection/backend has exited. Recover the outstanding event in the second.
    block = [path,expect("exists(select from campus_menu where published and version=1)","published after restart"),
             expect(f"exists(select from campus_event where id='{event}' and state='READY')","event after restart"),
             "begin",sql("claim_event",now),sql("deliver_page","",now,event,now),
             f"update campus_event set state='DONE' where id='{event}'","commit",insert_event(event),
             "insert into campus_student(id,enrolled_at) values('new-student','2099-01-01')",
             sql("deliver_page","",now,event,now),
             expect("(select count(*)=2 from campus_inbox) and (select count(*)=1 from campus_inbox where recipient='student')","dedup"),
             sql("edit_menu","Stale edit",False,menu,0),
             expect("(select dishes='Daal' and version=1 from campus_menu)","stale edit"),
             sql("rate_meal",str(uuid.uuid4()),1,menu),sql("rate_meal",str(uuid.uuid4()),5,menu)]
    for value in [0,6]:
        bad=sql("rate_meal",str(uuid.uuid4()),value,menu)
        block.append("do $$ begin "+bad+"; raise exception 'invalid rating accepted'; exception when check_violation then null; end $$")
    block.append(f"""insert into campus_case(ref,kind,body,status,created) values
        ('{complaint}','COMPLAINT','fixture A','OPEN','{now}'),
        ('{closed}','COMPLAINT','fixture B','RESOLVED','{now}'),
        ('{feedback}','FEEDBACK','fixture C','OPEN','{now}'),
        (gen_random_uuid(),'COMPLAINT','fixture D','CLOSED','{now}')""")
    block += [sql("escalate_cases","2026-11-17 23:59:59.999999+00",now),
              expect("not exists(select from campus_case where escalated is not null)","before threshold"),
              sql("escalate_cases",now,"2026-11-20 00:00+00"),sql("escalate_cases",now,"2026-11-21 00:00+00"),
              expect("(select count(*)=1 from campus_case where escalated is not null)","exclude resolved and feedback"),
              expect(f"(select escalated='2026-11-20 00:00+00' from campus_case where ref='{complaint}')","once"),
              sql("move_case","IN_REVIEW","IN_REVIEW",now,complaint,"OPEN"),
              sql("move_case","RESOLVED","RESOLVED",now,complaint,"IN_REVIEW"),
              expect(f"(select status='RESOLVED' and resolved is not null from campus_case where ref='{complaint}')","resolve")]
    run(block)
    print("PASS recovered committed event in a fresh backend; replay: one inbox row per student")
    print("PASS stale update, rating 0/1/5/6, threshold before/at, repeat escalation, resolution and exclusions")
    retry_id=str(uuid.uuid4())
    failed_insert=sql("deliver_target",retry_id,"late-fixture",now)
    retry_time="2026-11-18 00:00:01+00"
    run([path,sql("insert_event",retry_id,now,"BILL","late-fixture","Bill available",now),
         "begin",sql("claim_event",now),
         "do $$ begin "+failed_insert+"; raise exception 'FK failure expected'; exception when foreign_key_violation then null; end $$",
         sql("mark_failure",1,"READY",retry_time,retry_id),"commit",
         expect(f"(select attempts=1 and available>'{now}' from campus_event where id='{retry_id}')","retry delayed"),
         expect(f"not exists(select from campus_inbox where event_id='{retry_id}')","failure left no delivery"),
         "insert into campus_student(id) values('late-fixture')","begin",sql("claim_event",retry_time),
         sql("deliver_target",retry_id,"late-fixture",retry_time),sql("mark_progress",None,"DONE",retry_id),"commit",
         expect(f"(select count(*)=1 from campus_inbox where event_id='{retry_id}')","retry delivery")])
    print("PASS forced recipient FK failure, rolled-back delivery, delayed retry, successful durable recovery")
    bench=run([path,"create temp table timings(ms double precision)","""do $$
declare start_time timestamptz; i integer; found_id uuid;
begin for i in 1..2000 loop start_time=clock_timestamp();
 select e.id into strict found_id from campus_inbox i join campus_event e on e.id=i.event_id
 where i.recipient='student' order by i.delivered desc,i.event_id limit 1;
 insert into timings values(extract(epoch from clock_timestamp()-start_time)*1000);
 end loop; end $$""",
        """select 'MEASURE ops='||count(*)||' db_ops_per_s='||round((count(*)*1000/sum(ms))::numeric,1)||
        ' p95_ms='||round(percentile_cont(0.95) within group(order by ms)::numeric,3)||' errors=0 END' from timings"""])
    print(re.search(r'MEASURE ops=\d+ [^"\r\n]+? END',bench).group())
    print("Workload: 1 backend, 3 inbox rows (1 for measured student), 2,000 indexed hot reads in one PL/pgSQL loop; SQL-only timings.")
finally:
    if creation: run([f"drop schema {schema} cascade"])
