param(
    [ValidateRange(0,1000)][int]$PushAttempts = 0,
    [ValidateRange(1,300)][int]$InitialDelaySeconds = 5,
    [switch]$PublishRelease
)
$ErrorActionPreference = 'Stop'
try { $PSNativeCommandUseErrorActionPreference = $false } catch {}
$repo = 'liao-zc/OmniPlayer'
$workflow = 'library.yml'
$branch = (git branch --show-current).Trim()
if (-not $branch) { throw 'Unable to determine current Git branch.' }
$delay = $InitialDelaySeconds
$attempt = 0
while ($true) {
    $attempt++
    $attemptLabel = if ($PushAttempts -eq 0) { "$attempt (unlimited retries)" } else { "$attempt/$PushAttempts" }
    Write-Host "Push attempt $attemptLabel to origin/$branch"
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $pushOutput = & git -c http.version=HTTP/1.1 push --set-upstream origin "HEAD:$branch" 2>&1
    $pushCode = $LASTEXITCODE
    $ErrorActionPreference = $savedPreference
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
    if ($PushAttempts -gt 0 -and $attempt -ge $PushAttempts) { throw "Push failed after $PushAttempts attempts; check GitHub connectivity and rerun this script." }
    Write-Host "Push failed; retrying in $delay seconds."
    Start-Sleep -Seconds $delay
    $delay = [Math]::Min(60, $delay * 2)
}
Write-Host "Dispatching $workflow for $branch"
$savedPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$dispatchArgs = @('workflow', 'run', $workflow, '--repo', $repo, '--ref', $branch)
if ($PublishRelease) { $dispatchArgs += @('-f', 'publish_release=true') }
$dispatchOutput = & gh @dispatchArgs 2>&1
$dispatchCode = $LASTEXITCODE
$ErrorActionPreference = $savedPreference
$dispatchText = $dispatchOutput -join "`n"
$dispatchOutput | ForEach-Object { Write-Host $_ }
if ($dispatchCode -ne 0) { throw 'GitHub Actions dispatch failed.' }
$match = [regex]::Match($dispatchText, '/actions/runs/(\d+)')
if (-not $match.Success) { throw 'GitHub CLI did not return a run URL.' }
$runId = $match.Groups[1].Value
Write-Host "Watching run $runId"
gh run watch $runId --repo $repo --exit-status
$runCode = $LASTEXITCODE
$out = Join-Path (Get-Location) "artifacts/ci/$runId"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$artifactResponse = gh api "repos/$repo/actions/runs/$runId/artifacts"
if ($LASTEXITCODE -ne 0) { throw "Could not list artifacts for run $runId." }
$artifact = ($artifactResponse | ConvertFrom-Json).artifacts | Where-Object { $_.name -eq 'OmniPlayer-CI-report-and-debug-APK' } | Select-Object -First 1
if (-not $artifact) { throw "CI run $runId did not publish the expected APK/report artifact." }
$apkPath = Join-Path (Get-Location) 'dist/OmniPlayer-debug.apk'
$reportPath = Join-Path $out 'OmniPlayer-CI-Report.md'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/download_ci_artifact.ps1 -ArtifactId $artifact.id -EntryPath 'app/build/outputs/apk/debug/app-debug.apk' -OutputFile $apkPath
if ($LASTEXITCODE -ne 0) { throw "Could not extract the APK from run $runId." }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/download_ci_artifact.ps1 -ArtifactId $artifact.id -EntryPath 'manual-shots/OmniPlayer-CI-Report.md' -OutputFile $reportPath
if ($LASTEXITCODE -ne 0) { throw "Could not extract the report from run $runId." }
Write-Host "Downloaded APK to $apkPath and CI report to $reportPath"
if ($runCode -ne 0) { throw "CI run $runId failed. Inspect $out/OmniPlayer-CI-Report.md and the workflow logs." }
