param([string]$q, [int]$n = 8)
$input = "protocol=https`nhost=github.com`n`n"
$out = $input | git credential fill
$tok = ($out | Select-String '^password=') -replace 'password=', ''
$h = @{ Authorization = "Bearer $tok"; Accept = "application/vnd.github+json" }
$u = "https://api.github.com/search/code?q=" + [uri]::EscapeDataString($q) + "&per_page=" + $n
$r = Invoke-RestMethod -Headers $h -Uri $u
foreach ($i in $r.items) { $i.repository.full_name + ' :: ' + $i.path }
