$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $bin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
    $out = 'target/domain-check'
    New-Item -Force -ItemType Directory $out | Out-Null
    $base = 'src/main/java/com/giki/mess/campus'
    $sources = @(Get-ChildItem "$base/domain","$base/contract" -Filter '*.java' -ErrorAction SilentlyContinue | ForEach-Object FullName)
    & "$bin/javac" --release 21 -Xlint:all -Werror -d $out @sources "$base/alerts/DeliveryPool.java" src/test/java/com/giki/mess/campus/WorkerChecks.java src/test/java/com/giki/mess/campus/DomainChecks.java
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
    & "$bin/java" -cp $out com.giki.mess.campus.DomainChecks
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
    & "$bin/java" -cp $out com.giki.mess.campus.WorkerChecks
    exit $LASTEXITCODE
} finally { Pop-Location }
