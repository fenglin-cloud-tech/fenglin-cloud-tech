# 本地全栈一键启动 / 停止（幂等）
# 用法:
#   .\scripts\start-local.ps1                # 起中间件 → 迁移 → 服务 → 网关
#   .\scripts\start-local.ps1 -SkipInfra     # 中间件已在跑时跳过 docker 步骤
#   .\scripts\start-local.ps1 -SkipMigrate   # 跳过 Flyway
#   .\scripts\start-local.ps1 -Stop          # 只停本脚本/本仓库的 Java 服务（按端口+命令行校验）
#   .\scripts\start-local.ps1 -WaitSec 120   # 单个服务健康检查超时（默认 90 秒）
param(
    [switch]$Stop,
    [switch]$SkipInfra,
    [switch]$SkipMigrate,
    [switch]$KeepAlive,
    [int]$WaitSec = 90
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$logDir = Join-Path $root "work\logs-start"
$runDir = Join-Path $root "work\run"
$composeFile = Join-Path $root "deploy\compose\docker-compose.yml"
New-Item -ItemType Directory -Force -Path $logDir, $runDir | Out-Null

# ---------------------------------------------------------------- 清单（唯一来源）
# 依赖顺序：iam → auth → tenant（auth 依赖 iam，见部署手册 3.1 依赖表）
$infra = @(
    @{ name = "postgres";      port = 5432; label = "PostgreSQL" },
    @{ name = "redis";         port = 6379; label = "Redis" },
    @{ name = "nacos";         port = 8848; label = "Nacos" },
    @{ name = "kafka";         port = 9092; label = "Kafka" },
    @{ name = "elasticsearch"; port = 9201; label = "Elasticsearch" },
    @{ name = "minio";         port = 9000; label = "MinIO" }
)

$tiers = @(
    @{ tier = "基础中台"; services = @(
        @{ module = "fenglin-iam-service"; port = 18082 },
        @{ module = "fenglin-auth-service"; port = 18081 },
        @{ module = "fenglin-tenant-service"; port = 18083 }
    ) },
    @{ tier = "能力中台"; services = @(
        @{ module = "fenglin-workflow-service"; port = 18084 },
        @{ module = "fenglin-message-service"; port = 18085 },
        @{ module = "fenglin-file-service"; port = 18086 },
        @{ module = "fenglin-audit-service"; port = 18087 },
        @{ module = "fenglin-search-service"; port = 18088 },
        @{ module = "fenglin-report-service"; port = 18092 },
        @{ module = "fenglin-lowcode-service"; port = 18093 },
        @{ module = "fenglin-ai-service"; port = 18094 }
    ) },
    @{ tier = "业务域与开放平台"; services = @(
        @{ module = "fenglin-openapi-service"; port = 18091 },
        @{ module = "fenglin-crm-service"; port = 18095 },
        @{ module = "fenglin-collab-service"; port = 18096 },
        @{ module = "fenglin-talent-service"; port = 18097 },
        @{ module = "fenglin-finance-service"; port = 18098 },
        @{ module = "fenglin-rnd-service"; port = 18099 },
        @{ module = "fenglin-service-service"; port = 18100 },
        @{ module = "fenglin-legal-service"; port = 18101 },
        @{ module = "fenglin-intelligence-service"; port = 18102 },
        @{ module = "fenglin-mdm-service"; port = 18110 }
    ) },
    @{ tier = "网关"; services = @(
        @{ module = "fenglin-gateway"; port = 18080 }
    ) }
)

# 迁移：库 = schema = 目录（与各 *-migrations 模块一一对应）
$migrations = @(
    @{ db = "fenglin_hub";          module = "fenglin-hub-migrations";          dir = "migration" },
    @{ db = "fenglin_devops";       module = "fenglin-rnd-migrations";          dir = "rnd-migration" },
    @{ db = "fenglin_talent";       module = "fenglin-talent-migrations";       dir = "talent-migration" },
    @{ db = "fenglin_finance";      module = "fenglin-finance-migrations";      dir = "finance-migration" },
    @{ db = "fenglin_collab";       module = "fenglin-collab-migrations";       dir = "collab-migration" },
    @{ db = "fenglin_crm";          module = "fenglin-crm-migrations";          dir = "crm-migration" },
    @{ db = "fenglin_legal";        module = "fenglin-legal-migrations";        dir = "legal-migration" },
    @{ db = "fenglin_intelligence"; module = "fenglin-intelligence-migrations"; dir = "intelligence-migration" },
    @{ db = "fenglin_service";      module = "fenglin-service-migrations";      dir = "service-migration" },
    @{ db = "fenglin_mdm";          module = "fenglin-mdm-migrations";          dir = "mdm-migration" }
)

$rows = New-Object System.Collections.Generic.List[object]
function Add-Row($svc, $port, $status) {
    $rows.Add([pscustomobject]@{ Service = $svc; Port = $port; Status = $status }) | Out-Null
}

function Test-Port([int]$port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    return [bool]$conn
}

function Invoke-Native([string]$exe, [string[]]$nativeArgs) {
    # docker 把进度写到 stderr；PS5 在 Stop 偏好下会把 stderr 当成终止性错误，这里局部降级并统一取退出码
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $exe @nativeArgs 2>&1
        return @{ code = $LASTEXITCODE; out = @($out) }
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Get-ListenPid([int]$port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) { return [int]$conn.OwningProcess }
    return 0
}

function Get-JarPath([string]$module) {
    return Join-Path $root "backend\platform\$module\target\$module-0.1.0-SNAPSHOT.jar"
}

function Test-OurProcess([int]$procId) {
    # 只认本仓库 jar 启动的 Java 进程，避免误杀系统/他人的 Java
    if (-not $procId) { return $false }
    $p = Get-CimInstance Win32_Process -Filter "ProcessId = $procId" -ErrorAction SilentlyContinue
    if (-not $p) { return $false }
    return ($p.Name -match "^java(w)?\.exe$") -and ($p.CommandLine -match "fenglin-.*-0\.1\.0-SNAPSHOT\.jar")
}

function Wait-Health([int]$port, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$port/actuator/health" -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Show-LogTail([string]$module, [int]$lines = 25) {
    $out = Join-Path $logDir "$module.out.log"
    $err = Join-Path $logDir "$module.err.log"
    Write-Host "    ↓ $module 日志尾部（$out）" -ForegroundColor DarkYellow
    if (Test-Path $out) { Get-Content $out -Tail $lines | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray } }
    if (Test-Path $err) {
        $errTail = Get-Content $err -Tail $lines -ErrorAction SilentlyContinue
        if ($errTail) { $errTail | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkRed } }
    }
}

# ---------------------------------------------------------------- 停止模式
if ($Stop) {
    Write-Host "== 停止本机由本仓库 jar 启动的服务（按端口与命令行校验，不误杀其他 Java）==" -ForegroundColor Cyan
    $seen = @{}
    foreach ($tier in $tiers) {
        foreach ($svc in $tier.services) {
            if ($seen.ContainsKey($svc.port)) { continue }
            $seen[$svc.port] = $true
            $procId = Get-ListenPid $svc.port
            if (-not $procId) { Add-Row $svc.module $svc.port "未运行"; continue }
            if (Test-OurProcess $procId) {
                Stop-Process -Id $procId -Force
                Add-Row $svc.module $svc.port "已停止(pid=$procId)"
            } else {
                Add-Row $svc.module $svc.port "跳过：端口被其他进程占用(pid=$procId)"
            }
        }
    }
    $rows | Format-Table -AutoSize | Out-String | Write-Host
    exit 0
}

# ---------------------------------------------------------------- 1. 中间件
if (-not $SkipInfra) {
    Write-Host "== 1/4 中间件（按依赖顺序，已在跑的跳过）==" -ForegroundColor Cyan
    $dockerOk = $null -ne (Get-Command docker -ErrorAction SilentlyContinue)
    foreach ($item in $infra) {
        if (Test-Port $item.port) {
            Write-Host "  [跳过] $($item.label) :$($item.port) 已在监听"
            continue
        }
        if (-not $dockerOk) {
            Write-Host "  [失败] $($item.label) :$($item.port) 未启动，且本机没有 docker" -ForegroundColor Red
            Add-Row $item.label $item.port "缺失（无 docker）"
            continue
        }
        Write-Host "  [启动] $($item.label)（docker compose up -d $($item.name)）"
        $r = Invoke-Native "docker" @("compose", "-f", $composeFile, "up", "-d", $item.name)
        $r.out | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        $deadline = (Get-Date).AddSeconds(120)
        $up = $false
        while ((Get-Date) -lt $deadline) {
            if (Test-Port $item.port) { $up = $true; break }
            Start-Sleep -Seconds 3
        }
        if ($up) {
            Write-Host "  [就绪] $($item.label) :$($item.port)" -ForegroundColor Green
        } else {
            Write-Host "  [失败] $($item.label) 120 秒内未监听 :$($item.port)" -ForegroundColor Red
            $logs = Invoke-Native "docker" @("compose", "-f", $composeFile, "logs", "--tail", "25", $item.name)
            $logs.out | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
            Add-Row $item.label $item.port "启动失败"
        }
    }
} else {
    Write-Host "== 1/4 中间件：已按 -SkipInfra 跳过 ==" -ForegroundColor DarkYellow
}

# ---------------------------------------------------------------- 2. 迁移
if (-not $SkipMigrate) {
    Write-Host "== 2/4 数据库迁移（Flyway）==" -ForegroundColor Cyan
    $dockerOk = $null -ne (Get-Command docker -ErrorAction SilentlyContinue)
    if (-not $dockerOk) {
        Write-Host "  [失败] 未找到 docker，无法执行 Flyway 迁移" -ForegroundColor Red
    } else {
        foreach ($m in $migrations) {
            $sqlDir = Join-Path $root "backend\platform\$($m.module)\src\main\resources\db\$($m.dir)"
            if (-not (Test-Path $sqlDir)) {
                Write-Host "  [失败] $($m.db)：迁移目录不存在 $sqlDir" -ForegroundColor Red
                continue
            }
            Write-Host "  [迁移] $($m.db) ← $($m.module)/$($m.dir)"
            $r = Invoke-Native "docker" @(
                "run", "--rm", "-v", "${sqlDir}:/flyway/sql:ro", "flyway/flyway:10.17.0",
                "-url=jdbc:postgresql://host.docker.internal:5432/$($m.db)",
                "-user=fenglin", "-password=fenglin_dev",
                "-schemas=$($m.db)", "-defaultSchema=$($m.db)",
                "-locations=filesystem:/flyway/sql", "-mixed=true",
                "-postgresql.transactional.lock=false", "-connectRetries=10", "migrate"
            )
            $code = $r.code
            $tail = ($r.out | Select-Object -Last 4) -join "`n"
            if ($code -eq 0) {
                Write-Host "  [完成] $($m.db)" -ForegroundColor Green
            } else {
                Write-Host "  [失败] $($m.db)（exit=$code）" -ForegroundColor Red
                Write-Host "    $tail" -ForegroundColor DarkRed
                if ($tail -match "no schema history table|already exists") {
                    Write-Host "    提示：该库是历史手工建表/缺 Flyway 历史，属本地数据漂移；需人工确认后 flyway baseline/repair，脚本不做自动 baseline。" -ForegroundColor Yellow
                }
            }
        }
    }
} else {
    Write-Host "== 2/4 数据库迁移：已按 -SkipMigrate 跳过 ==" -ForegroundColor DarkYellow
}

# ---------------------------------------------------------------- 3/4. 服务 + 网关
foreach ($tier in $tiers) {
    Write-Host "== $($tier.tier) ==" -ForegroundColor Cyan
    foreach ($svc in $tier.services) {
        $procId = Get-ListenPid $svc.port
        if ($procId) {
            if (Test-OurProcess $procId) {
                Write-Host "  [跳过] $($svc.module) :$($svc.port) 已在运行(pid=$procId)"
                Add-Row $svc.module $svc.port "运行中"
            } else {
                Write-Host "  [冲突] $($svc.module) :$($svc.port) 被其他进程占用(pid=$procId)，未启动" -ForegroundColor Red
                Add-Row $svc.module $svc.port "端口冲突(pid=$procId)"
            }
            continue
        }
        $jar = Get-JarPath $svc.module
        if (-not (Test-Path $jar)) {
            Write-Host "  [失败] $($svc.module)：jar 不存在，请先构建（mvn package）" -ForegroundColor Red
            Add-Row $svc.module $svc.port "缺少 jar"
            continue
        }
        Write-Host "  [启动] $($svc.module) :$($svc.port)"
        $p = Start-Process -FilePath "java" `
            -ArgumentList "-jar", $jar, "--spring.profiles.active=dev" `
            -RedirectStandardOutput (Join-Path $logDir "$($svc.module).out.log") `
            -RedirectStandardError (Join-Path $logDir "$($svc.module).err.log") `
            -WindowStyle Hidden -PassThru
        Set-Content -Path (Join-Path $runDir "$($svc.module).pid") -Value $p.Id
        if (Wait-Health $svc.port $WaitSec) {
            Write-Host "  [就绪] $($svc.module)" -ForegroundColor Green
            Add-Row $svc.module $svc.port "健康"
        } else {
            Write-Host "  [失败] $($svc.module) 在 $WaitSec 秒内未通过 /actuator/health" -ForegroundColor Red
            Show-LogTail $svc.module
            Add-Row $svc.module $svc.port "健康检查超时"
        }
    }
}

# ---------------------------------------------------------------- 汇总
Write-Host ""
Write-Host "== 服务 / 端口 / 状态 ==" -ForegroundColor Cyan
$summary = ($rows | Format-Table -AutoSize | Out-String).TrimEnd()
Write-Host $summary
Set-Content -Path (Join-Path $logDir "startup-summary.txt") -Value $summary -Encoding UTF8
$failed = @($rows | Where-Object { $_.Status -notmatch "健康|运行中|已停止|未运行" })
if ($failed.Count -gt 0) {
    Write-Host "存在 $($failed.Count) 项异常，详见上方日志与日志尾部输出。" -ForegroundColor Red
    if (-not $KeepAlive) { exit 1 }
}
Write-Host "日志目录：$logDir" -ForegroundColor Green

if ($KeepAlive) {
    # 在受限的自动化会话里，父进程退出可能连带回收子进程；-KeepAlive 让启动器保持挂起
    Write-Host "已进入 -KeepAlive：保持挂起，Ctrl+C 结束（不会停止已启动的服务）。" -ForegroundColor DarkYellow
    $pids = Get-ChildItem $runDir -Filter "*.pid" -ErrorAction SilentlyContinue |
        ForEach-Object { [int](Get-Content $_.FullName -Raw).Trim() } |
        Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue }
    if ($pids) { Wait-Process -Id $pids }
}
