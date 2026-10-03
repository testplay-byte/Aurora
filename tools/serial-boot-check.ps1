# Serial boot check for Aurora ESP32-S3 (COM6, 115200)
# Performs an esptool-style hard reset (IO0 high -> normal app boot),
# then captures serial output for $seconds seconds.
param([string]$Port = "COM6", [int]$Baud = 115200, [int]$Seconds = 16)

$p = New-Object System.IO.Ports.SerialPort($Port, $Baud, "None", 8, "One")
$p.ReadBufferSize = 65536
$p.NewLine = "`n"
$p.Open()
Write-Host "[ps1] port $Port opened @ $Baud"

# --- esptool-style hard_reset: DTR false (IO0 high), pulse RTS (EN) ---
$p.DtrEnable = $false
$p.RtsEnable = $true
Start-Sleep -Milliseconds 120
$p.RtsEnable = $false
Start-Sleep -Milliseconds 50
Write-Host "[ps1] reset pulsed - reading for $Seconds s ..."

$deadline = (Get-Date).AddSeconds($Seconds)
$got = 0
while ((Get-Date) -lt $deadline) {
    try {
        if ($p.BytesToRead -gt 0) {
            $chunk = $p.ReadExisting()
            if ($chunk.Length -gt 0) {
                Write-Output $chunk
                $got += $chunk.Length
            }
        } else {
            Start-Sleep -Milliseconds 40
        }
    } catch {
        Write-Host "[ps1] read error: $($_.Exception.Message)"
        break
    }
}
$p.Close()
Write-Host "[ps1] done - captured $got chars"
