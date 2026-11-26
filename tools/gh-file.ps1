param([string]$repo, [string]$path)
$input = "protocol=https`nhost=github.com`n`n"
$out = $input | git credential fill
$tok = ($out | Select-String '^password=') -replace 'password=', ''
$h = @{ Authorization = "Bearer $tok"; Accept = "application/vnd.github+json" }
$info = Invoke-RestMethod -Headers $h -Uri ("https://api.github.com/repos/" + $repo)
$u = "https://api.github.com/repos/" + $repo + "/contents/" + [uri]::EscapeDataString($path) + "?ref=" + $info.default_branch
$f = Invoke-RestMethod -Headers $h -Uri $u
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($f.content))
