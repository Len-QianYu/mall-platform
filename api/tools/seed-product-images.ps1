param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$demoApiRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $demoApiRoot
try {
    $demoMaven = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($demoMaven) {
        & $demoMaven.Source -B -ntp -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/demo-image-classpath.txt'
    } else {
        & '.\mvnw.cmd' -B -ntp -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/demo-image-classpath.txt'
    }
    if ($LASTEXITCODE -ne 0) { throw 'Maven 编译或依赖准备失败' }
    $demoClasspath = (Get-Content -Raw -LiteralPath 'target/demo-image-classpath.txt').Trim() + ';target/classes'
    $demoArguments = @('--class-path', $demoClasspath, 'tools/SeedProductImages.java')
    if ($DryRun) { $demoArguments += '--dry-run' }
    & java @demoArguments
    if ($LASTEXITCODE -ne 0) { throw '演示图片初始化失败，数据库变更会在失败时回滚' }
} finally {
    Pop-Location
}
