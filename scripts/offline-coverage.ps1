param([Parameter(Mandatory)][string]$AgentJar)
$ErrorActionPreference='Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $bin=Join-Path $env:JAVA_HOME 'bin'
    & pwsh -NoProfile -File scripts/domain-check.ps1
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
    $files=@()
    foreach($suite in 'DomainChecks','WorkerChecks') {
        $dest="target/coverage-$([guid]::NewGuid().ToString('N')).ic"
        $files+=$dest
        & "$bin/java" "-javaagent:${AgentJar}=$dest false false false false com.giki.mess.campus.domain.* com.giki.mess.campus.contract.* com.giki.mess.campus.alerts.DeliveryPool" -cp target/domain-check "com.giki.mess.campus.$suite"
        if ($LASTEXITCODE) { exit $LASTEXITCODE }
    }
    & "$bin/java" -cp $AgentJar scripts/CoverageSummary.java @files
    exit $LASTEXITCODE
} finally { Pop-Location }
