param([string]$Out = "$env:TEMP\cl0udly-preview.png", [int]$TimeoutSec = 240)

Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("dwmapi.dll")] public static extern int DwmFlush();
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int W, int H, bool repaint);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int X, int Y, int W, int H, uint flags);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
}
"@

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$found = $null
while ((Get-Date) -lt $deadline -and -not $found) {
    $found = Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like '*desktop preview*' } |
        Select-Object -First 1
    if (-not $found) { Start-Sleep -Milliseconds 1500 }
}

if (-not $found) { Write-Output "NO_WINDOW"; exit 1 }

$h = $found.MainWindowHandle
[Win32]::ShowWindow($h, 9) | Out-Null
# Windows sometimes restores a maximized frame, and the editor window may sit on
# top: pin it, phone-sized, above everything for the shot.
[Win32]::SetWindowPos($h, [IntPtr]::new(-1), 40, 40, 390, 844, 0x0040) | Out-Null
[Win32]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Seconds 4

$r = New-Object Win32+RECT
[Win32]::GetWindowRect($h, [ref]$r) | Out-Null
$w = $r.Right - $r.Left
$hh = $r.Bottom - $r.Top

$bmp = New-Object System.Drawing.Bitmap($w, $hh)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Write-Output "SAVED $Out ${w}x${hh} pid=$($found.Id) title=$($found.MainWindowTitle)"
