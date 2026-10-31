param(
    [ValidateRange(1,20)][int]$PushAttempts = 8,
    [ValidateRange(1,300)][int]$InitialDelaySeconds = 5
)
$ErrorActionPreference = 'Stop'
$repo = 'liao-zc/OmniPlayer'
$workflow = 'library.yml'
$branch = (git branch --show-current).Trim()
if (-not $branch) { throw 'Unable to determine current Git branch.' }
$delay = $InitialDelaySeconds
for ($attempt = 1; $attempt -le $PushAttempts; $attempt++) {
    Write-Host "Push attempt $attempt/$PushAttempts to origin/$branch"
    $pushOutput = & git push --set-upstream origin "HEAD:$branch" 2>&1
    $pushCode = $LASTEXITCODE
    $pushOutput | ForEach-Object { Write-Host $_ }
    if ($pushCode -eq 0) { break }
    $pushText = $pushOutput -join "`n"
    if ($pushText -match 'fetch first|non-fast-forward|\[rejected\]') {
        Write-Host "Remote branch advanced; fetching and rebasing local commits."
        git fetch origin $branch
        if ($LASTEXITCODE -ne 0) { throw 'Could not fetch the advanced GitHub branch.' }
        git rebase "origin/$branch"
        if ($LASTEXITCODE -ne 0) { throw 'Rebase needs conflict resolution; local commits are preserved.' }
    }
    if ($attempt -eq $PushAttempts) { throw "Push failed after $PushAttempts attempts; check GitHub connectivity and rerun this script." }
    Write-Host "Push failed; retrying in $delay seconds."
    Start-Sleep -Seconds $delay
    $delay = [Math]::Min(60, $delay * 2)
}
Write-Host "Dispatching $workflow for $branch"
gh workflow run $workflow --repo $repo --ref $branch
if ($LASTEXITCODE -ne 0) { throw 'GitHub Actions dispatch failed.' }
$sha = (git rev-parse HEAD).Trim()
$runId = $null
for ($poll = 0; $poll -lt 24 -and -not $runId; $poll++) {
    $query = ".[] | select(.headSha==`"$sha`") | .databaseId"
    $runId = gh run list --repo $repo --workflow $workflow --branch $branch --limit 20 --json databaseId,headSha --jq $query
    if ($LASTEXITCODE -ne 0) { throw 'Could not query the dispatched Actions run.' }
    if (-not $runId) { Start-Sleep -Seconds 5 }
}
if (-not $runId) { throw 'Timed out waiting for the dispatched Actions run to appear.' }
Write-Host "Watching run $runId"
gh run watch $runId --repo $repo --exit-status
$runCode = $LASTEXITCODE
$out = Join-Path (Get-Location) "artifacts/ci/$runId"
New-Item -ItemType Directory -Force -Path $out | Out-Null
gh run download $runId --repo $repo --dir $out
if ($LASTEXITCODE -ne 0) { throw "Could not download run artifacts. Check run $runId on GitHub." }
Write-Host "Downloaded APK and report artifacts to $out"
if ($runCode -ne 0) { throw "CI run $runId failed. Inspect $out/OmniPlayer-CI-Report.md and the workflow logs." }