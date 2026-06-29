$text = Get-Content -Path 'D:\workspace\My\harmony\harmony-cams\harmony-cams\harmony_push_utf8.bat' -Raw -Encoding UTF8
$lines = $text -split "`n"
$crlfText = ($lines | ForEach-Object { $_.TrimEnd("`r") }) -join "`r`n"
$gbk = [System.Text.Encoding]::GetEncoding('GBK')
[System.IO.File]::WriteAllBytes('D:\workspace\My\harmony\harmony-cams\harmony-cams\harmony_push.bat', $gbk.GetBytes($crlfText))
Write-Host 'Done. Checking first 64 bytes:'
$b = [IO.File]::ReadAllBytes('D:\workspace\My\harmony\harmony-cams\harmony-cams\harmony_push.bat')
$b[0..63] | ForEach-Object { [Console]::Write(('{0:X2} ' -f $_)) }
[Console]::WriteLine()
