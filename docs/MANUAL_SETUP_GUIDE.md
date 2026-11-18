# Manual guide: tools, PostgreSQL, running and changing the project

Written for Windows 11 with the software already installed on this machine: JDK 22, Maven 3.9,
VS Code, Git, PostgreSQL 17 (includes pgAdmin 4 and psql). Port 5432.

## 1. Which software does what

| Software | Used for | Where to open it |
| --- | --- | --- |
| **VS Code** (+ "Extension Pack for Java") | write and edit all code, run tests, use the terminal | Start menu, or `code .` inside the project folder |
| **JDK 22** | compiles and runs Java | nothing to open; `java -version` in a terminal |
| **Maven** | downloads libraries, compiles, runs tests (`pom.xml`) | VS Code terminal: `mvn clean verify` |
| **PostgreSQL 17 server** | the database itself; runs as a Windows service named `postgresql-x64-17` | Start menu, "Services" app, find the service |
| **pgAdmin 4** | graphical tool to look at and change the database | Start menu, "pgAdmin 4" |
| **psql** | the same, in the command line | Start menu, "SQL Shell (psql)", or `& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost` |
| **Git / GitHub** | version history, pushing the branch | VS Code Source Control panel or terminal |

You never create database files by hand. PostgreSQL stores its own files in
`C:\Program Files\PostgreSQL\17\data`; you only send it SQL commands (through pgAdmin, psql or
the Java program) to create users, databases and tables.

## 2. One-time database setup

1. Open **SQL Shell (psql)**. Press Enter for server, database, port and user (defaults), then
   type the password you chose for the `postgres` superuser when installing PostgreSQL.
2. Create an application user and database:
   ```sql
   CREATE USER mess_user WITH PASSWORD 'choose-a-password';
   CREATE DATABASE mess_db OWNER mess_user;
   ```
3. Check: `\du` lists users, `\l` lists databases. Leave with `\q`.

(pgAdmin alternative: expand Servers, PostgreSQL 17, enter the postgres password; right-click
"Login/Group Roles", Create, Login/Group Role; then right-click "Databases", Create, Database,
owner `mess_user`.)

## 3. Create the tables

**Automatic (normal way):** the program runs `src/main/resources/schema.sql` at every start.
It only creates what is missing.

**Manual (to see or practise it):**
- pgAdmin: Servers, PostgreSQL 17, Databases, `mess_db`, right-click, **Query Tool**; click the
  folder icon, open `schema.sql`, press **F5**.
- or psql: `psql -U mess_user -d mess_db -h localhost -f "<path to>/src/main/resources/schema.sql"`

## 4. Check the tables exist

- pgAdmin: `mess_db`, Schemas, public, **Tables** (right-click, Refresh). You should see
  `student_ledgers`, `bills`, `bill_items`, `payment_transactions`.
- psql: `psql -U mess_user -d mess_db -h localhost`, then `\dt` (tables) and `\d bills` (columns
  and constraints).

## 5. Check that Java connects

In the VS Code terminal (View, Terminal) at the project folder:
```powershell
$env:MESS_DB_PASSWORD = "choose-a-password"     # lasts only for this terminal window
mvn -q compile exec:java
```
Expected: `Connecting with DatabaseConfig[... password=****]`, `Connected. Tables are ready.` and the
numbered steps 1 to 7 of the demo.

| Message | Meaning and fix |
| --- | --- |
| `MESS_DB_PASSWORD is not set` | set the variable in this terminal (command above) |
| `password authentication failed` | wrong password for `mess_user` |
| `role "mess_user" does not exist` | do section 2 |
| `database "mess_db" does not exist` | do section 2 |
| `Connection refused` | server not running: open Services, start `postgresql-x64-17` |

To keep the password for every new terminal: `setx MESS_DB_PASSWORD "choose-a-password"`, then
close and reopen VS Code.

## 6. See the data the program wrote

In pgAdmin Query Tool on `mess_db`:
```sql
SELECT student_id, balance_paisa / 100.0 AS balance_pkr, unbilled_meals, status FROM student_ledgers;
SELECT bill_id, net_paisa / 100.0 AS net_pkr, status, due_date FROM bills;
SELECT COUNT(*) FROM bill_items;
SELECT transaction_id, amount_paisa / 100.0 AS pkr, channel, status FROM payment_transactions;
```
Or right-click a table, View/Edit Data, All Rows.

## 7. Making changes

- **Change data:** `UPDATE student_ledgers SET status = 'SUSPENDED' WHERE student_id = '2099012';`
  Try breaking a rule on purpose to see the database defend itself:
  `UPDATE student_ledgers SET balance_paisa = -5;` is refused by the CHECK constraint.
- **Clear the demo data:**
  `TRUNCATE payment_transactions, bill_items, bills, student_ledgers;`
- **Change the structure:** `schema.sql` only creates missing tables, it does not alter existing
  ones. Either run `ALTER TABLE ... ADD COLUMN ...` in pgAdmin, or during development drop the
  tables (`DROP TABLE payment_transactions, bill_items, bills, student_ledgers;`) and start the
  program again so it recreates them.
- **Change code:** edit in VS Code, then `mvn clean verify` (all tests and the coverage check), then
  `mvn -q compile exec:java` for the demo. Then `git add`, `git commit`, `git push`.

## 8. If you were writing this project from nothing

1. Install JDK 22, Maven, VS Code (+ Java extension pack), Git, PostgreSQL 17.
2. VS Code: Command Palette, "Java: Create Java Project", Maven. This makes `pom.xml` and the
   `src/main/java` and `src/test/java` folders.
3. In `pom.xml` add the libraries (JUnit, PostgreSQL driver, HikariCP, logging) and save; Maven
   downloads them.
4. Write a failing test in `src/test/java`, run it (`mvn test`), write the code in
   `src/main/java`, run again, commit. Repeat (this is TDD).
5. Write `schema.sql` in `src/main/resources`, create the user and database in psql (section 2).
6. Write the repositories that run SQL through JDBC; run the program; look at the tables in pgAdmin.
7. Push to GitHub; the workflow in `.github/workflows/ci.yml` runs `mvn verify` automatically.
