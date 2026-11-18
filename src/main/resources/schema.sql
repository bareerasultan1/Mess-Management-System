create table if not exists campus_student (
    id varchar(100) primary key
);
create table if not exists campus_menu (
    id uuid primary key, day date not null, meal varchar(12) not null check (meal in ('BREAKFAST','LUNCH','DINNER')),
    dishes varchar(500) not null check (length(trim(dishes)) > 0), special boolean not null,
    published boolean not null default false, version bigint not null default 0,
    unique(day,meal)
);
create table if not exists campus_event (
    id uuid primary key, occurred timestamptz not null, kind varchar(16) not null,
    recipient varchar(100), summary varchar(250) not null, state varchar(8) not null default 'READY',
    available timestamptz not null, attempts smallint not null default 0, cursor_id varchar(100),
    check (state in ('READY','DONE','DEAD')),
    check ((kind in ('BILL','DUE','OVERDUE') and recipient is not null)
        or (kind in ('MENU','ANNOUNCEMENT','SPECIAL_MEAL') and recipient is null))
);
create index if not exists campus_event_ready on campus_event(available,id) where state='READY';
create table if not exists campus_inbox (
    event_id uuid not null references campus_event(id), recipient varchar(100) not null references campus_student(id),
    delivered timestamptz not null, primary key(event_id,recipient)
);
create index if not exists campus_inbox_page on campus_inbox(recipient,delivered desc,event_id);
create table if not exists campus_case (
    ref uuid primary key, kind varchar(10) not null check (kind in ('COMPLAINT','FEEDBACK')),
    body varchar(2000) not null check (length(trim(body)) > 0),
    status varchar(12) not null default 'OPEN' check(status in ('OPEN','IN_REVIEW','RESOLVED','CLOSED')),
    created timestamptz not null, escalated timestamptz, resolved timestamptz
);
create index if not exists campus_case_page on campus_case(created desc,ref);
create index if not exists campus_case_due on campus_case(created,ref) where kind='COMPLAINT' and escalated is null and status in ('OPEN','IN_REVIEW');
create index if not exists campus_case_escalated on campus_case(created desc,ref) where escalated is not null;
create table if not exists campus_rating (
    id uuid primary key, menu_id uuid not null references campus_menu(id), score smallint not null check(score between 1 and 5)
);

create table if not exists campus_reminder (
    bill_id varchar(100) primary key, recipient varchar(100), due_day date, next_at timestamptz,
    check (next_at is null or (recipient is not null and due_day is not null))
);
create index if not exists campus_reminder_due on campus_reminder(next_at,bill_id) where next_at is not null;
