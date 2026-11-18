"""Contention scenarios for the disposable standalone PostgreSQL verification cluster."""
from contextlib import contextmanager
from uuid import uuid4


def check_held_locks(run, sql, expect, schema):
    def session(*statements):
        return run([f"set search_path to {schema}", "set lock_timeout='300ms'",
                    "set statement_timeout='5s'", *statements])

    @contextmanager
    def held(*commands, commit=False):
        # PREPARE retains real PostgreSQL row locks after backend exit.
        gid = "m2_lock_" + uuid4().hex
        session("begin", *commands, f"prepare transaction '{gid}'")
        try:
            yield
        except BaseException:
            session(f"rollback prepared '{gid}'")
            raise
        else:
            decision = "commit" if commit else "rollback"
            session(f"{decision} prepared '{gid}'")

    def rows(statement, expected):
        return f"""do $$ declare n integer; begin
          execute $query$ {statement} $query$; get diagnostics n = row_count;
          if n <> {expected} then raise exception 'expected {expected} rows, got %', n; end if;
        end $$"""

    def must_wait(statement):
        # Only 55P03 passes. Deadlock, statement timeout and success are failures.
        return f"""do $$ begin
          execute $query$ {statement} $query$;
          raise exception 'competing write succeeded while locked';
        exception when lock_not_available then null; end $$"""

    menu = str(uuid4())
    session(f"""insert into campus_menu(id,day,meal,dishes,special,published)
                values('{menu}','2180-01-01','DINNER','Original',false,true)""")
    first = sql("edit_menu", "First revision", False, menu, 0)
    stale = sql("edit_menu", "Stale revision", False, menu, 0)
    with held(rows(first, 1), commit=True):
        session(expect(f"(select dishes='Original' and version=0 from campus_menu where id='{menu}')",
                       "reader saw uncommitted edit"), must_wait(stale))
    session(rows(stale, 0), rows(sql("edit_menu", "Fresh revision", False, menu, 1), 1),
            expect(f"(select version=2 and dishes='Fresh revision' from campus_menu where id='{menu}')",
                   "fresh edit did not progress"))
    print("PASS published menu: reader saw previous commit; competing editor got 55P03 while held; "
          "stale edit affected 0 rows after commit; refreshed edit succeeded")

    early, later = str(uuid4()), str(uuid4())
    at, after = "2025-01-01 00:00:00+00", "2025-01-02 00:00:00+00"
    session(sql("insert_event", early, at, "MENU", None, "Early", at),
            sql("insert_event", later, after, "MENU", None, "Later", after))
    claim = sql("claim_event", after)

    def picks(event):
        return expect(f"(select id='{event}' from ({claim}) pick) is true", "wrong or missing claim")

    with held(picks(early)):
        session("begin", picks(later), sql("deliver_target", later, "student", after),
                sql("mark_progress", None, "DONE", later), "commit",
                expect(f"not exists({claim})", "locked event claimed"))
    session("begin", picks(early), sql("deliver_target", early, "student", after),
            sql("mark_progress", None, "DONE", early), "commit",
            expect(f"not exists({claim})", "queue did not drain"),
            expect(f"(select count(*)=2 from campus_inbox where event_id in ('{early}','{later}'))",
                   "missing or repeated delivery"))
    print("PASS outbox: locked oldest event skipped; second event delivered; all-locked poll empty; "
          "rolled-back claim reclaimed; 2 events done and exactly 2 receipts")

    duplicate = str(uuid4())
    session(sql("insert_event", duplicate, at, "BILL", "student", "Bill ready", at))
    receipt = sql("deliver_target", duplicate, "student", after)
    with held(rows(receipt, 1), commit=True):
        session(must_wait(receipt))
    session(rows(receipt, 0),
            expect(f"(select count(*)=1 from campus_inbox where event_id='{duplicate}' and recipient='student')",
                   "duplicate receipt"))
    print("PASS duplicate delivery: second insert contended on the unique key while held; "
          "replay after commit inserted 0 rows; exactly 1 receipt")

    resolving, eligible = str(uuid4()), str(uuid4())
    session(f"""insert into campus_case(ref,kind,body,status,created) values
       ('{resolving}','COMPLAINT','test','IN_REVIEW','{at}'),
       ('{eligible}','COMPLAINT','test','OPEN','{at}')""")
    escalation = sql("escalate_cases", at, after)
    with held(rows(sql("move_case", "RESOLVED", "RESOLVED", after, resolving, "IN_REVIEW"), 1), commit=True):
        session(rows(escalation, 1), rows(escalation, 0),
                expect(f"(select escalated is not null from campus_case where ref='{eligible}')",
                       "other complaint did not progress"))
    session(rows(escalation, 0),
            expect(f"(select status='RESOLVED' and escalated is null from campus_case where ref='{resolving}')",
                   "resolved complaint escalated"))
    print("PASS resolution first: escalation skipped locked complaint, escalated its neighbor once, "
          "excluded resolved complaint after commit")

    case = str(uuid4())
    session(f"""insert into campus_case(ref,kind,body,status,created)
                values('{case}','COMPLAINT','test','IN_REVIEW','{at}')""")
    resolve = sql("move_case", "RESOLVED", "RESOLVED", after, case, "IN_REVIEW")
    with held(rows(escalation, 1), commit=True):
        session(rows(escalation, 0), must_wait(resolve))
    session(rows(resolve, 1), rows(escalation, 0),
            expect(f"(select status='RESOLVED' and escalated='{after}' from campus_case where ref='{case}')",
                   "escalation history lost"))
    print("PASS escalation first: second scan skipped held row; concurrent resolve got 55P03; "
          "resolution after release retained the one escalation")
