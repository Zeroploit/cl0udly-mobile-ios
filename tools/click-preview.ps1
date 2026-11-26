# Click inside the cl0udly desktop preview window at window-relative coordinates.
# Usage: powershell -File tools\click-preview.ps1 -X 40 -Y 63 [-Count 1] [-Dy 0] [-Dx 0]
param([int]$X, [int]$Y, [int]$Count = 1, [int]$Dx = 0, [int]$Dy = 0)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Click {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
}
"@

$proc = Get-Process | Where-Object { $_.MainWindowTitle -like '*desktop preview*' } | Select-Object -First 1
if (-not $proc) { Write-Output "NO_WINDOW"; exit 1 }
$h = $proc.MainWindowHandle
[Click]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 400

$r = New-Object Click+RECT
[Click]::GetWindowRect($h, [ref]$r) | Out-Null
$px = $r.Left + $X
$py = $r.Top + $Y

for ($i = 0; $i -lt $Count; $i++) {
    [Click]::SetCursorPos($px, $py) | Out-Null
    Start-Sleep -Milliseconds 120
    [Click]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)  # down
    Start-Sleep -Milliseconds 90
    [Click]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)  # up
    Start-Sleep -Milliseconds 350
    $px += $Dx; $py += $Dy
}
Write-Output "CLICKED $X,$Y x$Count at screen($($r.Left + $X),$($r.Top + $Y))"
