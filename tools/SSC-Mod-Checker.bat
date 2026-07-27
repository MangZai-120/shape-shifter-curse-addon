@echo off
chcp 65001 >nul
title SSC / SSCA Mod Checker
powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:CHECK_DIR='%~dp0'; $c=[IO.File]::ReadAllText('%~f0',[Text.Encoding]::UTF8); $i=$c.LastIndexOf([char]35+'PS_START'+[char]35); iex $c.Substring($i+10)"
echo.
pause
exit /b
#PS_START#
# ================= SSC / SSCA 模组自检工具（PowerShell 部分）=================
# 放到任意 mods 目录，双击本 .bat 即可自动扫描该目录所有 jar。
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
Add-Type -AssemblyName System.IO.Compression.FileSystem

$dir = $env:CHECK_DIR
if (-not $dir) { $dir = (Get-Location).Path }

# ---- SSC / SSCA 版本要求（如日后变化改这里）----
$REQ_GECKOLIB = '4.8.4'
$REQ_SSC      = '1.10.0'
$REQ_TRINKETS = '3.7.2'

# 版本比较：current < required 返回 $true（只比前 3 段数字 major.minor.patch）
function VerBelow($cur, $req) {
    try {
        $c = ("$cur" -split '[^0-9]') | Where-Object { $_ -ne '' }
        $r = ("$req" -split '[^0-9]') | Where-Object { $_ -ne '' }
        for ($k = 0; $k -lt 3; $k++) {
            $cv = if ($k -lt $c.Count) { [int]$c[$k] } else { 0 }
            $rv = if ($k -lt $r.Count) { [int]$r[$k] } else { 0 }
            if ($cv -lt $rv) { return $true }
            if ($cv -gt $rv) { return $false }
        }
        return $false
    } catch { return $false }
}

# ---- 扫描所有 jar，读 fabric.mod.json 的真实 mod id + version ----
$mods = @{}         # id -> 列表 @{ ver; file }
$unknown = @()      # 无 fabric.mod.json 的 jar
$jars = @(Get-ChildItem -LiteralPath $dir -Filter *.jar -File -ErrorAction SilentlyContinue)
foreach ($jar in $jars) {
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
        $entry = $zip.GetEntry('fabric.mod.json')
        if (-not $entry) { $entry = $zip.GetEntry('quilt.mod.json') }
        $id = $null; $ver = $null
        if ($entry) {
            $sr = New-Object System.IO.StreamReader($entry.Open())
            $txt = $sr.ReadToEnd(); $sr.Close()
            try {
                $j = $txt | ConvertFrom-Json
                if ($j.id) { $id = $j.id; $ver = "$($j.version)" }
                elseif ($j.quilt_loader -and $j.quilt_loader.id) { $id = $j.quilt_loader.id; $ver = "$($j.quilt_loader.version)" }
            } catch {
                if ($txt -match '"id"\s*:\s*"([^"]+)"') { $id = $matches[1] }
                if ($txt -match '"version"\s*:\s*"([^"]+)"') { $ver = $matches[1] }
            }
        }
        $zip.Dispose()
        if ($id) {
            if (-not $mods.ContainsKey($id)) { $mods[$id] = @() }
            $mods[$id] += @{ ver = $ver; file = $jar.Name }
        } else {
            $unknown += $jar.Name
        }
    } catch { $unknown += $jar.Name }
}

# ---- 输出（同时写入报告文件）----
$report = New-Object System.Collections.ArrayList
function Say($line) { Write-Host $line; [void]$report.Add($line) }

$problems = 0
Say '============================================================'
Say ' SSC / SSCA 模组自检工具'
Say " 扫描目录: $dir"
Say '============================================================'
Say ''
Say "共扫描 $($jars.Count) 个 jar，识别出 $($mods.Count) 个 Fabric 模组。"
Say ''

# 【1】重复模组
Say '【1】重复模组检查（同一 mod 装了多个版本，会互相冲突）'
$dupFound = $false
foreach ($id in ($mods.Keys | Sort-Object)) {
    if ($mods[$id].Count -gt 1) {
        $dupFound = $true; $problems++
        $files = ($mods[$id] | ForEach-Object { "$($_.file) (v$($_.ver))" }) -join '  |  '
        Say "  [问题] 重复: $id 装了 $($mods[$id].Count) 个  ->  $files"
    }
}
if (-not $dupFound) { Say '  [通过] 未发现重复模组' }
Say ''

$hasSSC   = $mods.ContainsKey('shape-shifter-curse')
$hasAddon = $mods.ContainsKey('ssc_addon')

if ($hasSSC -or $hasAddon) {
    # 【2】GeckoLib
    Say "【2】GeckoLib 版本检查（SSC 形态渲染，需 >= $REQ_GECKOLIB）"
    if ($mods.ContainsKey('geckolib')) {
        $gv = $mods['geckolib'][0].ver
        if (VerBelow $gv $REQ_GECKOLIB) {
            $problems++
            Say "  [问题] geckolib v$gv 过低！会导致所有形态显示为白色人类模型（动作正常但模型全白）。"
            Say "         解决：升级 geckolib 到 >= $REQ_GECKOLIB"
        } else {
            Say "  [通过] geckolib v$gv 满足要求"
        }
    } else {
        $problems++
        Say '  [问题] 未找到 geckolib！SSC 需要它，否则形态无法渲染。'
    }
    Say ''

    # 【3】SSC / SSCA 依赖
    Say '【3】SSC / SSCA 依赖版本检查'
    if ($hasSSC) {
        $sv = $mods['shape-shifter-curse'][0].ver
        if (VerBelow $sv $REQ_SSC) {
            $problems++
            Say "  [问题] 幻型者诅咒本体 v$sv 过低！SSCA 需要 >= $REQ_SSC"
        } else {
            Say "  [通过] 幻型者诅咒本体 v$sv 满足要求"
        }
    } elseif ($hasAddon) {
        $problems++
        Say '  [问题] 装了 SSCA(ssc_addon) 但缺少幻型者诅咒本体(shape-shifter-curse)！'
    }
    if ($hasAddon) {
        if ($mods.ContainsKey('trinkets')) {
            $tv = $mods['trinkets'][0].ver
            if (VerBelow $tv $REQ_TRINKETS) {
                $problems++
                Say "  [问题] trinkets(饰品栏) v$tv 过低！SSCA 需要 >= $REQ_TRINKETS"
            } else {
                Say "  [通过] trinkets v$tv 满足要求"
            }
        } else {
            $problems++
            Say "  [问题] 装了 SSCA 但缺少 trinkets(饰品栏)！SSCA 需要 >= $REQ_TRINKETS"
        }
    } else {
        Say '  [跳过] 未检测到 SSCA(ssc_addon)，跳过 SSCA 专属依赖检查。'
    }
    Say ''
} else {
    Say '【2/3】未检测到 SSC / SSCA，跳过其版本检查。'
    Say ''
}

if ($unknown.Count -gt 0) {
    Say "【附】$($unknown.Count) 个 jar 无 fabric.mod.json（多为 Forge mod / 资源包，未参与检查）:"
    foreach ($u in ($unknown | Sort-Object)) { Say "    - $u" }
    Say ''
}

Say '============================================================'
if ($problems -eq 0) {
    Say ' 结果: 未发现问题 [通过]'
} else {
    Say " 结果: 发现 $problems 个问题 [问题]  请按上面提示处理。"
}
Say '============================================================'

try {
    $reportPath = Join-Path $dir '_模组自检报告.txt'
    $report | Out-File -LiteralPath $reportPath -Encoding UTF8
    Write-Host ''
    Write-Host "报告已保存: $reportPath"
} catch {
    Write-Host ''
    Write-Host "（报告文件写入失败: $($_.Exception.Message)）"
}
