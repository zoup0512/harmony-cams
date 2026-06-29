$b = [IO.File]::ReadAllBytes('D:\workspace\My\harmony\harmony-cams\harmony-cams\harmony_push.bat')
$b[0..63] | ForEach-Object { [Console]::Write(('{0:X2} ' -f $_)) }
[Console]::WriteLine()
[Console]::WriteLine('Total bytes: ' + $b.Length)
