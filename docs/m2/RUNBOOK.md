# Run the three-module demo

Use PowerShell 7 in the repository. The initial build needs JDK 21+, PostgreSQL 17 and access to Maven Central. The script uses Maven on PATH if available. Otherwise it downloads Maven 3.9.9 and checks the Apache distribution's pinned SHA-512. It creates a private loopback PostgreSQL cluster under `.demo` on port 55487 and a database called `campus_demo`. The interactive portal binds to loopback on port 8080.

```powershell
cd D:\Downloads\SCaDSQAProject\Mess-Management-System
git branch --show-current
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
pwsh -File scripts/demo.ps1 -Action Verify
$env:MESS_DEMO_PASSWORD=Read-Host 'Choose a password for the four local demo users'
pwsh -File scripts/demo.ps1 -Action Run
```

Use `-PgBin 'C:\Program Files\PostgreSQL\17\bin'` if PostgreSQL lives elsewhere (substitute its actual path). Open **http://127.0.0.1:8080**. Demo users are `staff`, `student`, `student2` and `admin`. Without an explicit password the launcher prints a random demo password for that run. Stop the web app with Ctrl+C. Stop its private database with `pwsh -File scripts/demo.ps1 -Action Stop`. Repeat launches retain demo records. Git ignores the local cluster, its keys and downloaded tools.

## Five-minute walkthrough

1. Sign in as **staff**. Create a draft for an unused date and meal. In a separate private browser window, sign in as **student** and filter to that slot. The draft is hidden.
2. Open a second copy of the staff page. Edit dishes in the first copy, then save different dishes from the older copy. The stale form shows a conflict. Refresh before continuing.
3. Publish the current version. The staff receipt displays the event ID and fills the replay field. Refresh the student's menu and inbox. Check the meal and its single notification. Replay the delivered event as staff and refresh the inbox again. Publishing a special meal produces a SPECIAL_MEAL event.
4. As student, rate the published meal and submit a complaint. Save its private reference, sign out and track it. The response displays status. Staff can read the private message and move OPEN → IN_REVIEW → RESOLVED → CLOSED.
5. For a quick escalation demo, set `$env:MESS_ESCALATION='PT15S'` **before starting the demo**. Submit a new complaint and leave it open. Sign in as admin and refresh escalations after the interval. Change the setting back with `Remove-Item Env:MESS_ESCALATION` and restart for the proposed 48-hour default. The short setting applies to every eligible case in this demo database.
6. As staff, send the labeled ANNOUNCEMENT, BILL, DUE and OVERDUE fixtures. Announcement is broadcast. The three billing fixtures create different unpaid demo obligations for `student`. A due/overdue fixture produces a bill-issued alert and the appropriate reminder. Sign in as `student2` to check that the targeted alerts belong only to `student`.

## Verification commands

The Verify action runs `mvn -B clean verify pmd:pmd`. JUnit uses a fresh randomly named schema in the explicit test database and drops that schema on normal JVM exit. An interrupted test JVM may leave its isolated schema for later cleanup. Reports appear under `target/surefire-reports`, `target/site/jacoco` and Maven's PMD output directory. List current PMD findings in the milestone record before treating the gate as reviewed.

For an existing PostgreSQL database, configure a role that owns the demo database and can create schemas in the test database. Grant only the required database privileges. Under the standard demo launcher these are prepared automatically.

```powershell
$env:MESS_DB_URL='jdbc:postgresql://127.0.0.1:5432/campus_demo'
$env:MESS_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/campus_test'
$env:MESS_DB_USER='campus'
$env:MESS_DB_PASSWORD=Read-Host 'Database password'
mvn -B clean verify pmd:pmd
$env:SPRING_PROFILES_ACTIVE='demo'
$env:MESS_DEMO_PASSWORD=Read-Host 'Demo sign-in password'
mvn spring-boot:run
```

From a second shell with the same `MESS_DEMO_PASSWORD`, run `python scripts/http-check.py --clients 8 --reads 20`. This creates one fixture menu, measures publish-to-visible-inbox time, waits for persisted DONE after replay and verifies the count. It then measures concurrent HTTP reads from independently authenticated sessions for the same demo student. Specify `--clients 500` only for a planned load run. This endpoint workload alone cannot establish the system-wide 500-user target.

## Checks that also work without Maven

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
pwsh -File scripts/domain-check.ps1
& "$env:JAVA_HOME/bin/java" scripts/SourceCheck.java
$pg='C:\Program Files\PostgreSQL\17\bin'
# New, disposable directory only. Keep this standalone cluster stopped.
& "$pg/initdb.exe" -D .demo/standalone -U m2 -A scram-sha-256 -W -E UTF8 --locale=C
python scripts/postgres-check.py --pg-bin $pg --standalone-data .demo/standalone
```

Create `.demo` first with `New-Item -Force -ItemType Directory .demo` if the demo launcher has never run. The SQL suite also accepts `--database campus_test` for a running test server using standard PostgreSQL connection environment variables. It creates and removes its own random schema, opens fresh backends and loads the SQL resources used by the application.

For offline subset coverage, use `pwsh -File scripts/offline-coverage.ps1 -AgentJar <local-IntelliJ-coverage-agent.jar>`. For browser-only verification, make Playwright available to Node (a local installation or NODE_PATH), set `CHROME_BIN` to a Chromium executable and run `node scripts/ui-check.cjs`. The browser test explicitly intercepts API calls. The live HTTP check uses the running Spring application.

## Integration and operating limits

- `MESS_ZONE` defaults to Asia/Karachi. `MESS_ESCALATION` accepts a positive ISO duration. The reminder scheduler uses local calendar dates, including the local midnight boundary. Polling occurs every second for business deadlines and every 250 ms for delivery.
- Production needs an agreed identity provider and student roster. The default profile contains no demo users or fixture endpoints. The server derives inbox ownership from the authenticated principal. Its ID must exactly match the roster and the producer's recipient ID.
- Inspect outstanding delivery with `select state,count(*) from campus_event group by state;` and `select id,kind,attempts,available from campus_event where state='DEAD';`. Fix the missing recipient or other cause before a controlled retry. Replay of completed events through the UI exists only in the demo profile.
- Application logging excludes request bodies and SQL bindings. The demo launcher configures PostgreSQL's service role to suppress bind values, failing statements and verbose error detail. Apply equivalent database and proxy logging settings when integrating on a shared server.

## Reproduce same-record contention

With the stopped, disposable standalone cluster from the offline setup, run:

```powershell
python scripts/postgres-check.py --pg-bin 'C:\Program Files\PostgreSQL\17\bin' --standalone-data .demo/standalone --held-locks
```

This mode temporarily enables prepared transactions for its own backends. It uses a new schema, releases each held transaction and removes the schema. The full Maven Verify action also runs PostgresBehaviorTest, including a waiting-editor test and a deliberately inverted two-row lock order as a deadlock detection control.
