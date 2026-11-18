param(
    [ValidateSet('Run','Verify','Stop')][string]$Action='Run',
    [string]$PgBin,
    [int]$Port=55487
)
# PowerShell 7. Uses a private local cluster independent of the system service.
$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
function Check-Exit([string]$operation) {
    if ($LASTEXITCODE -ne 0) { throw "$operation failed (exit $LASTEXITCODE)." }
}
try {
    if (!$PgBin) {
        $cmd=Get-Command pg_ctl -ErrorAction SilentlyContinue
        $PgBin=if($cmd){Split-Path $cmd.Source}else{Join-Path $env:ProgramFiles 'PostgreSQL\17\bin'}
    }
    if (!(Test-Path "$PgBin/pg_ctl.exe")) { throw 'Pass -PgBin with your PostgreSQL bin directory.' }
    if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Choose a port from 1024 to 65535.' }
    $localDir=Join-Path (Get-Location) '.demo'
    $data=Join-Path $localDir 'pgdata'
    if ($Action -eq 'Stop') {
        if (Test-Path "$data/postmaster.pid") { & "$PgBin/pg_ctl.exe" stop -D $data -m fast; Check-Exit 'Stop demo PostgreSQL' }
        return
    }
    if (!$env:JAVA_HOME -and (Test-Path "$env:ProgramFiles/Java/jdk-21/bin/javac.exe")) {
        $env:JAVA_HOME="$env:ProgramFiles/Java/jdk-21"
    }
    if (!$env:JAVA_HOME -or !(Test-Path "$env:JAVA_HOME/bin/javac.exe")) { throw 'Set JAVA_HOME to JDK 21 or newer.' }
    New-Item -ItemType Directory -Force $localDir | Out-Null
    $maven=Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($maven) { $mvn=$maven.Source }
    else {
        $version='3.9.9'
        $mvn=Join-Path $localDir "apache-maven-$version/bin/mvn.cmd"
        if (!(Test-Path $mvn)) {
            $name="apache-maven-$version-bin.zip"
            $uri="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/$name"
            $zip=Join-Path $localDir $name
            Invoke-WebRequest $uri -OutFile $zip -TimeoutSec 60
            # Published at archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip.sha512
            $checksum='8beac8d11ef208f1e2a8df0682b9448a9a363d2ad13ca74af43705549e72e74c9378823bf689287801cbbfc2f6ea9596201d19ccacfdfb682ee8a2ff4c4418ba'
            if ((Get-FileHash $zip -Algorithm SHA512).Hash -ne $checksum) { throw 'Maven distribution checksum mismatch.' }
            Expand-Archive $zip $localDir -Force
        }
    }
    $ownerKey=Join-Path $localDir 'cluster-password'
    $appKey=Join-Path $localDir 'app-password'
    foreach ($file in $ownerKey,$appKey) {
        if (!(Test-Path $file)) { [IO.File]::WriteAllText($file,[guid]::NewGuid().ToString('N')) }
    }
    if (!(Test-Path "$data/PG_VERSION")) {
        & "$PgBin/initdb.exe" -D $data -U m2owner -A scram-sha-256 "--pwfile=$ownerKey" -E UTF8 --locale=C
        Check-Exit 'Initialize demo database'
    }
    & "$PgBin/pg_ctl.exe" status -D $data *> $null
    if ($LASTEXITCODE -ne 0) {
        & "$PgBin/pg_ctl.exe" start -D $data -l "$localDir/postgres.log" -o "-h 127.0.0.1 -p $Port" -w
        Check-Exit 'Start demo database'
    }
    $env:PGPASSWORD=[IO.File]::ReadAllText($ownerKey).Trim()
    $env:MESS_DB_PASSWORD=[IO.File]::ReadAllText($appKey).Trim()
    $env:MESS_DB_USER='campus'
    $env:MESS_DB_URL="jdbc:postgresql://127.0.0.1:$Port/campus_demo"
    $env:MESS_TEST_DB_URL=$env:MESS_DB_URL
    $pgArgs=@('-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',"$Port",'-U','m2owner','-d','postgres')
    @'
\getenv app_password MESS_DB_PASSWORD
select format('create role campus login password %L', :'app_password') where not exists(select from pg_roles where rolname='campus') \gexec
select 'create database campus_demo owner campus' where not exists(select from pg_database where datname='campus_demo') \gexec
alter role campus set log_parameter_max_length_on_error=0;
alter role campus set log_min_error_statement='panic';
alter role campus set log_error_verbosity='terse';
'@ | & "$PgBin/psql.exe" @pgArgs
    Check-Exit 'Prepare scoped demo role and database'
    Remove-Item Env:PGPASSWORD
    if ($Action -eq 'Verify') {
        & $mvn -B clean verify pmd:pmd
        Check-Exit 'Build, PostgreSQL tests, coverage and PMD'
    } else {
        $env:SPRING_PROFILES_ACTIVE='demo'
        $env:MESS_BIND='127.0.0.1'
        if (!$env:MESS_DEMO_PASSWORD) {
            $env:MESS_DEMO_PASSWORD=[guid]::NewGuid().ToString('N').Substring(0,14)
            Write-Host "Local demo sign-in password (this run): $env:MESS_DEMO_PASSWORD"
        }
        Write-Host 'Open http://127.0.0.1:8080 and sign in as staff, student, student2, or admin.'
        & $mvn spring-boot:run
        Check-Exit 'Spring Boot demo'
    }
} finally { Pop-Location }
