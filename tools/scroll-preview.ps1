# Scroll the cl0udly desktop preview window with the mouse wheel.
# Usage: powershell -File tools\scroll-preview.ps1 -X 195 -Y 500 -Notches -5
param([int]$X = 195, [int]$Y = 500, [int]$Notches = -5)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Scr {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, int dwData, UIntPtr dwExtraInfo);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
}
"@

$p = Get-Process | Where-Object { $_.MainWindowTitle -like '*desktop preview*' } | Select-Object -First 1
if (-not $p) { Write-Output "NO_WINDOW"; exit 1 }
$h = $p.MainWindowHandle
[Scr]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 400

$r = New-Object Scr+RECT
[Scr]::GetWindowRect($h, [ref]$r) | Out-Null
[Scr]::SetCursorPos($r.Left + $X, $r.Top + $Y) | Out-Null
Start-Sleep -Milliseconds 200

for ($i = 0; $i -lt [Math]::Abs($Notches); $i++) {
    $d = if ($Notches -lt 0) { -120 } else { 120 }
    [Scr]::mouse_event(0x0800, 0, 0, $d, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 180
}
Write-Output "SCROLLED $Notches notches at $X,$Y"
