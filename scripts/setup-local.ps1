param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "..\deploy\compose\docker-compose.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "未找到 docker，请先安装 Docker Desktop 并启动。"
}

$args = @("compose", "-f", $composeFile, "up", "-d")
if ($Build) {
    $args += "--build"
}
& docker @args

Write-Host ""
Write-Host "中间件已启动，等待健康检查..."
& docker compose -f $composeFile ps
Write-Host ""
Write-Host "PostgreSQL: localhost:5432 (fenglin_hub / fenglin / fenglin_dev)"
Write-Host "Nacos:      http://localhost:8848/nacos"
Write-Host "MinIO:      http://localhost:9001 (minioadmin / minioadmin123)"
Write-Host "Kafka:      localhost:9092"
Write-Host "Elasticsearch: http://localhost:9200"
