param([Parameter(Mandatory=$true)][string]$Path)
$ErrorActionPreference = 'Stop'
Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json | ForEach-Object {
    $result = $_
    $final = $result.final
    $target = @($final.files | Where-Object { $_.index -eq $final.currentFile }) | Select-Object -First 1
    [PSCustomObject]@{
        Hash = $result.hash
        File = $target.path
        Metadata = $final.metadata
        Peers = $final.peers
        DownloadedBytes = $final.downloaded
        PayloadBytes = $final.payloadBytes
        Passed = $result.passed
        Message = $final.playerError
    }
} | Format-Table -Wrap -AutoSize
