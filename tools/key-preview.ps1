# Send a key to the cl0udly desktop preview window.
# Usage: powershell -File tools\key-preview.ps1 -Key '{ESC}'
param([string]$Key = '{ESC}')

Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System; using System.Runtime.InteropServices;
public class FGKey { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
"@

$p = Get-Process | Where-Object { $_.MainWindowTitle -like '*desktop preview*' } | Select-Object -First 1
if (-not $p) { Write-Output "NO_WINDOW"; exit 1 }
[FGKey]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 500
[System.Windows.Forms.SendKeys]::SendWait($Key)
Start-Sleep -Milliseconds 800
Write-Output "SENT $Key to pid=$($p.Id)"
