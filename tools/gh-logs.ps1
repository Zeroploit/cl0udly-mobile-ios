param([string]$run = "")
$input = "protocol=https`nhost=github.com`n`n"
$out = $input | git credential fill
$tok = ($out | Select-String '^password=') -replace 'password=', ''
$h = @{ Authorization = "Bearer $tok"; Accept = "application/vnd.github+json" }
if ($run -eq "") {
    $runs = Invoke-RestMethod -Headers $h -Uri "https://api.github.com/repos/Zeroploit/cl0udly-mobile-ios/actions/runs?per_page=3"
    foreach ($r in $runs.workflow_runs) { $r.id.ToString() + " " + $r.name + " " + $r.conclusion + " " + $r.head_sha.Substring(0,7) }
    exit
}
$jobs = Invoke-RestMethod -Headers $h -Uri ("https://api.github.com/repos/Zeroploit/cl0udly-mobile-ios/actions/runs/" + $run + "/jobs")
foreach ($j in $jobs.jobs) {
    "=== job " + $j.id + " " + $j.name + " " + $j.conclusion
    $log = Invoke-WebRequest -Headers $h -Uri ("https://api.github.com/repos/Zeroploit/cl0udly-mobile-ios/actions/jobs/" + $j.id + "/logs") -UseBasicParsing
    $text = $log.Content
    $lines = $text -split "`n"
    $errs = $lines | Select-String -Pattern "^.*error:|error: |FAILED|Undefined symbol|ld: |Compilation failed|e: " | Select-Object -Last 60
    $errs
}
