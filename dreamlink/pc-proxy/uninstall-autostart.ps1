# Remove the DreamLink proxy logon task created by install-autostart.ps1.
$ErrorActionPreference = 'SilentlyContinue'
Stop-ScheduledTask    -TaskName 'DreamLink Proxy'
Unregister-ScheduledTask -TaskName 'DreamLink Proxy' -Confirm:$false
Write-Host '已移除开机自启任务（若存在）。本地 state\ 数据未删除，需要请手动清理。'
