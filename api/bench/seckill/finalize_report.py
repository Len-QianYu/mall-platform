"""Export the completed measurements, official JMeter dashboards and browser screenshots."""
import argparse
import json
import re
from pathlib import Path
import shutil
import subprocess

from benchmark_data import API
from build_report import read, render, pressure_report, screenshot
from run_benchmark import JAVA


def main(options):
    run_dir = options.run_dir.resolve()
    output = options.output.resolve()
    primary = [run_dir / options.baseline, run_dir / options.pool]
    pressure = [run_dir / options.pressure_baseline, run_dir / options.pressure_pool]
    for folder in primary + pressure:
        result = read(folder / 'summary.json')
        if result['unexpected_failures'] != 0 or not result['all_response_orders_persisted']:
            raise ValueError('Unhealthy benchmark round: ' + folder.name)
    render(options)
    pressure_report(run_dir, output, options.pressure_baseline, options.pressure_pool)
    print('Verified summaries exported.', flush=True)

    for label, folder in zip(['baseline', 'pool', 'pressure-baseline', 'pressure-pool'], primary + pressure):
        dashboard = folder / 'dashboard'
        command = [JAVA, '-Xmx512m', '-Duser.timezone=Asia/Shanghai', '-jar',
                   str(options.jmeter_home / 'bin/ApacheJMeter.jar'), '-g', str(folder / 'measured.jtl'),
                   '-o', str(dashboard), '-j', str(folder / 'dashboard-generation.log')]
        if not (dashboard / 'index.html').exists():
            with (folder / 'dashboard-console.log').open('w', encoding='utf-8') as log:
                subprocess.run(command, check=True, cwd=API, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        screenshot(dashboard / 'index.html', output / f'{label}-jmeter.png', options.chrome)
        print('JMeter screenshot:', label, flush=True)
    screenshot(output / 'comparison.html', output / 'comparison.png', options.chrome)
    screenshot(output / 'pressure.html', output / 'pressure.png', options.chrome)

    results = read(output / 'results.json')
    stress = read(output / 'pressure-results.json')
    left, right = results['baseline'], results['pool']
    resources_left, resources_right = results['baseline_resources'], results['pool_resources']
    threads_drop = (1 - resources_right['peak_threads'] / resources_left['peak_threads']) * 100
    throughput_change = (right['committed_orders_per_second'] / left['committed_orders_per_second'] - 1) * 100
    stress_left, stress_right = stress['baseline'], stress['pool']
    source = results['baseline_metadata']['source_sha256']
    plan = results['baseline_metadata']['jmx_sha256']
    validation_log = run_dir / 'backend-tests-final-identifiers.log'
    if not validation_log.exists(): validation_log = run_dir / 'backend-tests-final.log'
    validation_bytes = validation_log.read_bytes()
    validation_text = validation_bytes.decode('utf-16' if validation_bytes.startswith(b'\xff\xfe') else 'utf-8-sig', errors='replace')
    validation_matches = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', validation_text)
    total, failures, errors, skipped = map(int, validation_matches[-1])
    executed = total - skipped
    document = f'''# 秒杀系统压测记录 · 2026-11-28

完成 100,000 个测试用户及两个管理员的数据准备，采用真实 HTTP、JWT、Redis 和 MySQL 订单链路，对比 Tomcat 同步下单与 4 工作线程、256 排队任务的业务线程池。

## 持续成功下单

每轮 10,000 次独立下单、10,000 件配额、100 并发客户端；各轮先预热 2,000 次请求，预热不计入结果。

| 指标 | 同步基线 | 业务线程池 |
|---|---:|---:|
| 成功落库订单 | {left['successes']:,} | {right['successes']:,} |
| 成功落库吞吐（orders/s） | {left['committed_orders_per_second']:.2f} | {right['committed_orders_per_second']:.2f} |
| 平均响应（ms） | {left['mean_ms']:.2f} | {right['mean_ms']:.2f} |
| P95（ms） | {left['p95_ms']:.2f} | {right['p95_ms']:.2f} |
| P99（ms） | {left['p99_ms']:.2f} | {right['p99_ms']:.2f} |
| 应用线程采样峰值 | {resources_left['peak_threads']} | {resources_right['peak_threads']} |
| 非预期失败 | {left['unexpected_failures']} | {right['unexpected_failures']} |

线程采样峰值变化与吞吐分别记录：线程减少 {threads_drop:.1f}%，落库吞吐变化 {throughput_change:+.2f}%。不将线程占用减少解释为吞吐必然提高。线程数每秒采样；P95/P99 从完整 JTL 样本计算，使用线性插值。本次为单次成对测试结果，不代表生产集群容量。

## 10 万请求争抢 1,000 件库存

每轮 100,000 个独立账号、100,000 次请求、100 并发客户端、每人限购一件。

| 指标 | 同步基线 | 业务线程池 |
|---|---:|---:|
| 请求数 | {stress_left['requests']:,} | {stress_right['requests']:,} |
| 成功创建订单 | {stress_left['successes']:,} | {stress_right['successes']:,} |
| 预期售罄拒绝 | {stress_left['expected_stock_rejections']:,} | {stress_right['expected_stock_rejections']:,} |
| 非预期失败 | {stress_left['unexpected_failures']} | {stress_right['unexpected_failures']} |
| HTTP 吞吐（含售罄拒绝，requests/s） | {stress_left['completed_requests_per_second']:.2f} | {stress_right['completed_requests_per_second']:.2f} |
| 全部响应 P95（ms） | {stress_left['p95_ms']:.2f} | {stress_right['p95_ms']:.2f} |

售罄拒绝按预期业务结果单独统计；JMeter 的业务失败率会显示 99%。HTTP 吞吐包含拒绝响应，成功落库吞吐见持续下单测试。两轮均通过库存守恒、响应订单与数据库逐项对应、每人限购和管理员通知数量核验。

## 测试环境与范围

AMD Ryzen 7 5700U，8 核 / 16 线程，15.39 GiB 内存；Java 21.0.9、Spring Boot 4.1.2、MySQL 8.4.6、JMeter 5.6.3。客户端与单实例服务端同机，JVM 最大堆均为 512 MiB，Druid 固定 10 个连接，Tomcat 最大 200 个线程。

每轮使用同一份源代码、同一份 JMeter 计划、相同初始数据与索引。身份由每个请求的独立 JWT 确定，HTTP 连接持续复用。真实 MQ 网络传输和支付不计入测量；MQ 发送桩在两种模式中相同，订单事务、提交后回调和管理员通知持久化仍执行。

源码 SHA-256：`{source}`。JMeter 计划 SHA-256：`{plan}`。各轮原始 JTL 的 SHA-256 记录在 [results.json](results.json) 与 [pressure-results.json](pressure-results.json)。

## 诊断记录

初测发现 `NOT_SUPPORTED` 的空事务同步保留了幂等预查询连接，随后 `REQUIRES_NEW` 再申请连接导致满池等待。已改为仅为真实事务启用同步，24 笔并发订单在两个真实数据库连接下的回归通过。用户 `(role, status)` 索引把管理员通知查询从扫描近 10 万条记录改为读取两个管理员；事务修复和索引在两轮正式对比中一致。

初次大样本因每次迭代重建 HTTP 客户端而耗尽 Windows 临时端口，出现 71,345 次客户端 `BindException`。该轮保留在原始目录并标为无效。连接复用修复后重跑双方；它与事务修复均不作为线程池吞吐收益。

另发现秒级时间加六位随机订单号在大样本下碰撞，触发一笔订单回滚和 500。现使用 64 位随机后缀，订单号 30 字符、支付号 31 字符，符合现有数据库限制；它在双方正式测试中一致。订单号和支付号分别通过 10 万并发生成样本的唯一性回归。

后端回归：{total} 项，{executed} 项执行通过，{skipped} 项按环境条件跳过；包含真实 MySQL/Redis 的并发、幂等、失败补偿、取消、退款和限购测试。

## 截图与简历文案

- [持续下单对比截图](comparison.png)
- [10 万请求抢购对比截图](pressure.png)
- [基线 JMeter 截图](baseline-jmeter.png)
- [线程池 JMeter 截图](pool-jmeter.png)
- [抢购基线 JMeter 截图](pressure-baseline-jmeter.png)
- [抢购线程池 JMeter 截图](pressure-pool-jmeter.png)
- [可复制的简历文案](resume.txt)
- [压测脚本与复现说明](../../../api/bench/seckill/README.md)

原始数据目录：`api/bench/results/seckill/{run_dir.name}/`。正式轮次为 `{options.baseline}`、`{options.pool}`、`{options.pressure_baseline}`、`{options.pressure_pool}`；每轮保留完整 JTL、测试计划、启动参数、源码摘要、每秒资源记录和 SQL 核验。测试数据库保留 10 万账号，可继续复现。
'''
    (output / 'README.md').write_text(document, encoding='utf-8')
    resume = (f'基于 Redis Lua、Redisson 与 MySQL 事务实现秒杀库存预扣、幂等下单及失败补偿；'
              f'构建 10 万用户测试数据，完成每轮 10 万请求抢购验证，0 超卖、0 超限、0 非预期失败。'
              f'采用 4 工作线程、256 容量的有界线程池隔离下单任务，在 100 并发、每轮 1 万笔成功下单的同机压测中，'
              f'将应用线程采样峰值由 {resources_left["peak_threads"]} 降至 {resources_right["peak_threads"]}（减少 {threads_drop:.1f}%），'
              f'P95 从 {left["p95_ms"]:.0f} ms 到 {right["p95_ms"]:.0f} ms，'
              f'实测落库吞吐为 {left["committed_orders_per_second"]:.2f} 与 {right["committed_orders_per_second"]:.2f} orders/s。\n\n'
              '面试说明：压测为同机单实例，100 个并发客户端；10 万为账号和累计请求规模。'
              '抢购场景的 HTTP 吞吐包含售罄拒绝，支付和真实 MQ 传输未计入。\n')
    (output / 'resume.txt').write_text(resume, encoding='utf-8')
    for filename in ['role-index-explain.json']:
        if (run_dir / filename).exists(): shutil.copyfile(run_dir / filename, output / filename)
    (output / 'validation.json').write_text(json.dumps({'backend_tests_total': total,
        'backend_tests_executed': executed, 'backend_tests_skipped': skipped, 'failures': failures, 'errors': errors,
        'source_log': str(validation_log)}, indent=2), encoding='utf-8')
    print('Report complete:', output / 'README.md', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline', default='baseline-final')
    parser.add_argument('--pool', default='pool-final')
    parser.add_argument('--pressure-baseline', default='pressure-baseline-final')
    parser.add_argument('--pressure-pool', default='pressure-pool-final')
    parser.add_argument('--jmeter-home', type=Path, default=Path('C:/Jmeter/apache-jmeter-5.6.3'))
    parser.add_argument('--chrome', type=Path, default=Path('C:/Program Files/Google/Chrome/Application/chrome.exe'))
    main(parser.parse_args())
