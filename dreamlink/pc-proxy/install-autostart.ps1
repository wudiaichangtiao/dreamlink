# Start DreamLink proxy automatically when this user logs in.
# Run once from PowerShell. No admin needed: it registers a per-user logon task.
# Uses Register-ScheduledTask cmdlets (not schtasks) so non-ASCII paths survive.
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot

# Locate pythonw.exe (windowless Python). Fall back to python.exe location.
$pyw = $null
foreach ($c in @('pythonw','pythonw.exe')) {
    $g = Get-Command $c -ErrorAction SilentlyContinue
    if ($g) { $pyw = $g.Source; break }
}
if (-not $pyw) {
    $py = Get-Command python -ErrorAction SilentlyContinue
    if ($py) {
        $cand = Join-Path (Split-Path $py.Source) 'pythonw.exe'
        if (Test-Path $cand) { $pyw = $cand }
    }
}
if (-not $pyw) {
    Write-Error '找不到 pythonw.exe / python.exe。请先安装 Python 3 并勾选 "Add to PATH"，再重跑本脚本。'
    exit 1
}

$taskName = 'DreamLink Proxy'
$script   = Join-Path $here 'dreamlink_proxy.py'

$action   = New-ScheduledTaskAction -Execute $pyw -Argument "`"$script`"" -WorkingDirectory $here
$trigger  = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero)

# Replace any previous registration, then start it now.
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings | Out-Null
Start-ScheduledTask -TaskName $taskName

Write-Host "已注册并启动开机自启任务：'$taskName'"
Write-Host "  运行： $pyw `"$script`""
Write-Host "  数据： $here\state"
Write-Host "  API：  http://127.0.0.1:8756/api/links"
