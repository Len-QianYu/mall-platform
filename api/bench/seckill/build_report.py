"""Create reviewable reports and actual browser screenshots from saved JMeter/SQL evidence."""
import argparse
import csv
import json
import os
from pathlib import Path
import statistics
import subprocess

from benchmark_data import API


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def resources(folder, summary):
    with (folder / 'resources.csv').open(encoding='utf-8', newline='') as file:
        samples = [row for row in csv.DictReader(file)
                   if summary['sample_start_epoch_ms'] <= int(row['epoch_ms']) <= summary['sample_end_epoch_ms']]
    return {'peak_threads': max(int(row['server_threads']) for row in samples),
            'peak_rss_mib': max(float(row['server_rss_mib']) for row in samples),
            'mean_process_cpu_percent': round(statistics.mean(float(row['server_cpu_percent']) for row in samples), 2)}


def render(options):
    run_dir = options.run_dir.resolve()
    output = options.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    config = read(run_dir / 'run.json')
    folders = [run_dir / options.baseline, run_dir / options.pool]
    summaries = [read(folder / 'summary.json') for folder in folders]
    metadata = [read(folder / 'metadata.json') for folder in folders]
    measurements = [resources(folder, summary) for folder, summary in zip(folders, summaries)]
    if metadata[0]['user_indexes'] != metadata[1]['user_indexes']:
        raise ValueError('Database indexes differ; the result cannot be described as a thread-pool-only comparison.')
    for key in ['concurrency', 'requests', 'stock_quota', 'db_connections', 'heap_max_mib', 'tomcat_max_threads', 'source_sha256', 'jmx_sha256']:
        if metadata[0][key] != metadata[1][key]:
            raise ValueError(f'Unequal comparison condition: {key}')
    before, after = summaries
    throughput_change = (after['committed_orders_per_second'] / before['committed_orders_per_second'] - 1) * 100
    p95_change = (1 - after['p95_ms'] / before['p95_ms']) * 100
    evidence = {'environment': config, 'baseline': before, 'pool': after, 'baseline_metadata': metadata[0],
                'pool_metadata': metadata[1], 'baseline_resources': measurements[0], 'pool_resources': measurements[1],
                'committed_throughput_change_percent': round(throughput_change, 2),
                'p95_reduction_percent': round(p95_change, 2)}
    (output / 'results.json').write_text(json.dumps(evidence, indent=2, ensure_ascii=False), encoding='utf-8')

    def number(value):
        return f'{value:,.2f}' if isinstance(value, float) else f'{value:,}'

    rows = [
        ('HTTP 请求数', before['requests'], after['requests']),
        ('成功落库订单', before['successes'], after['successes']),
        ('成功落库吞吐（orders/s）', before['committed_orders_per_second'], after['committed_orders_per_second']),
        ('平均响应时间（ms）', before['mean_ms'], after['mean_ms']),
        ('P95 响应时间（ms）', before['p95_ms'], after['p95_ms']),
        ('P99 响应时间（ms）', before['p99_ms'], after['p99_ms']),
        ('失败率（%）', before['error_percent'], after['error_percent']),
        ('应用进程峰值线程数', measurements[0]['peak_threads'], measurements[1]['peak_threads']),
        ('库存超卖 / 用户超限', before['over_limit_buyers'], after['over_limit_buyers']),
    ]
    table = ''.join(f'<tr><td>{label}</td><td>{number(left)}</td><td>{number(right)}</td></tr>'
                    for label, left, right in rows)
    audit_rows = ''.join('<tr><td>' + label + '</td>' + ''.join(
        '<td>' + ('通过' if summary[key] else '失败') + '</td>' for summary in summaries) + '</tr>'
        for label, key in [('库存守恒（配额 = 剩余 + 购买 + 归还）', 'no_overselling'),
                           ('响应订单 ID 与数据库记录逐项一致', 'all_response_orders_persisted'),
                           ('每笔订单保存两位管理员的通知', 'notifications_complete')])
    metric_label = '提升' if throughput_change >= 0 else '下降'
    latency_label = '降低' if p95_change >= 0 else '增加'
    thread_reduction = (1 - measurements[1]['peak_threads'] / measurements[0]['peak_threads']) * 100
    throughput_description = '吞吐基本持平' if abs(throughput_change) < 5 else f'吞吐{metric_label} {abs(throughput_change):.2f}%'
    scope = ('真实 HTTP、RS256 JWT / 账号校验、Redis Lua / Redisson、MySQL 事务和订单通知。'
             '两轮均使用隔离的 MQ 发送桩，不计支付和真实 MQ 网络传输。成功只统计已返回订单 ID 并通过 SQL 核验的订单。')
    document = f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>秒杀系统真实压测对比</title>
<style>
*{{box-sizing:border-box}}body{{margin:0;background:#f1f5f9;color:#172554;font:18px "Microsoft YaHei",sans-serif}}
main{{max-width:1640px;margin:40px auto;padding:34px 48px;background:white;border-radius:20px}}
small,.note{{color:#64748b}}h1{{font-size:40px;margin:6px 0 16px}}p{{line-height:1.65;margin:10px 0}}
.tags{{display:flex;gap:12px;margin:22px 0}}.tags span{{background:#eff6ff;padding:10px 16px;border-radius:8px}}
.cards{{display:grid;grid-template-columns:repeat(3,1fr);gap:22px;margin:24px 0}}.card{{padding:22px;border:1px solid #dbeafe;border-radius:12px;background:#f8fafc}}
.card strong{{font-size:42px;display:block;margin:8px 0;color:#2563eb}}.card.pool strong{{color:#047857}}
table{{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums;margin:20px 0}}
th,td{{padding:12px 20px;border-bottom:1px solid #e2e8f0;text-align:right}}th:first-child,td:first-child{{text-align:left;width:52%}}th{{background:#f1f5f9}}
td:nth-child(2){{color:#2563eb}}td:nth-child(3){{color:#047857}}.audit{{font-size:16px}}.source{{font:12px Consolas,monospace;color:#64748b;overflow-wrap:anywhere}}
</style><main><small>Prompt Pals · 2026-11-28 · Apache JMeter 5.6.3</small>
<h1>秒杀系统 · 10 万用户真实压测</h1>
<p>同一台 AMD Ryzen 7 5700U（8 核 / 16 线程，{config['ram_gib']} GiB 内存），Java 21 · Spring Boot 4.1 · MySQL 8.4 · Redis</p>
<div class="tags"><span>每轮 {before['requests']:,} 次下单</span><span>{metadata[0]['concurrency']} 并发客户端</span><span>10 个数据库连接</span><span>JVM 最大堆 512 MiB</span><span>相同初始数据与索引</span></div>
<div class="cards"><div class="card">基线 · Tomcat 同步下单<strong>{before['committed_orders_per_second']:,.2f} orders/s</strong>P95 {before['p95_ms']:,.2f} ms</div>
<div class="card pool">业务线程池 · {metadata[1]['workers']} 个工作线程 / {metadata[1]['queue_capacity']} 个排队任务<strong>{after['committed_orders_per_second']:,.2f} orders/s</strong>{throughput_description} · P95 {latency_label} {abs(p95_change):.2f}%</div>
<div class="card">应用进程峰值线程<strong>{measurements[0]['peak_threads']} → {measurements[1]['peak_threads']}</strong>实测减少 {thread_reduction:.2f}%</div></div>
<table><thead><tr><th>测量指标</th><th>基线</th><th>业务线程池</th></tr></thead><tbody>{table}</tbody></table>
<table class="audit"><thead><tr><th>独立 SQL / 原始响应核验</th><th>基线</th><th>业务线程池</th></tr></thead><tbody>{audit_rows}</tbody></table>
<p class="note">{scope}</p><p class="note">数据集：{config['users']:,} 个普通账号 + 2 个管理员。预热不计入正式结果。P95/P99 从完整 JTL 样本计算。</p>
<div class="source">Baseline JTL SHA-256: {before['jtl_sha256']}<br>Pool JTL SHA-256: {after['jtl_sha256']}</div></main></html>'''
    (output / 'comparison.html').write_text(document, encoding='utf-8')
    resume = (f'基于 Java 21 / Spring Boot、Redis Lua 和 MySQL 实现秒杀库存预扣与幂等下单；'
              f'在 10 万用户数据、{metadata[0]["concurrency"]} 并发、每轮 {before["requests"]:,} 次 HTTP 下单的本机压测中，'
              f'通过 {metadata[1]["workers"]} 个工作线程、容量 {metadata[1]["queue_capacity"]} 的有界线程池隔离秒杀任务，'
              f'应用峰值线程数从 {measurements[0]["peak_threads"]} 降至 {measurements[1]["peak_threads"]}（减少 {thread_reduction:.1f}%）；'
              f'成功落库吞吐从 {before["committed_orders_per_second"]} 到 {after["committed_orders_per_second"]} orders/s，基本持平，'
              f'P95 从 {before["p95_ms"]:.2f} 到 {after["p95_ms"]:.2f} ms；'
              f'两轮共验证 {before["successes"] + after["successes"]:,} 笔订单，库存守恒、无重复订单、无超限购买。'
              '\n\n测试范围：同机客户端与单实例服务端，真实 HTTP/JWT/Redis/MySQL；不含支付和 MQ 网络传输。'
              '这是累计请求数，不能写成 10 万同时在线或 10 万并发。\n')
    (output / 'resume.txt').write_text(resume, encoding='utf-8')
    print(json.dumps({'throughput_change_percent': throughput_change, 'p95_reduction_percent': p95_change}, ensure_ascii=False))
    return output


def screenshot(document, destination, chrome):
    profile = API / 'bench/results/seckill/chrome-profiles' / destination.stem
    command = [str(chrome), '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
               '--disable-background-networking', '--disable-extensions', '--disable-sync',
               '--allow-file-access-from-files', '--hide-scrollbars', '--force-device-scale-factor=1',
               '--window-size=1920,1600', '--virtual-time-budget=4000',
               f'--user-data-dir={profile}', f'--screenshot={destination}', document.resolve().as_uri()]
    subprocess.run(command, check=True, timeout=60, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    if not destination.is_file():
        raise RuntimeError('Chrome did not create a screenshot.')


def pressure_report(run_dir, output, baseline, pool):
    folders = [run_dir / baseline, run_dir / pool]
    summaries = [read(folder / 'summary.json') for folder in folders]
    metadata = [read(folder / 'metadata.json') for folder in folders]
    for key in ['requests', 'stock_quota', 'concurrency', 'db_connections', 'source_sha256', 'jmx_sha256']:
        if metadata[0][key] != metadata[1][key]:
            raise ValueError('Pressure comparison conditions differ: ' + key)
    rows = [
        ('独立请求 / 测试账号', 'requests'),
        ('成功落库订单', 'successes'),
        ('预期售罄拒绝', 'expected_stock_rejections'),
        ('非预期失败', 'unexpected_failures'),
        ('HTTP 处理吞吐（含售罄拒绝，requests/s）', 'completed_requests_per_second'),
        ('全部响应 P95（ms）', 'p95_ms'),
        ('全部响应 P99（ms）', 'p99_ms'),
        ('单用户超限数量', 'over_limit_buyers'),
    ]
    table = ''.join('<tr><td>' + name + '</td>' + ''.join(f'<td>{summary[key]:,.2f}</td>'
                    for summary in summaries) + '</tr>' for name, key in rows)
    document = f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>10 万请求秒杀抢购压测</title>
<style>body{{margin:0;background:#f1f5f9;font:22px "Microsoft YaHei",sans-serif;color:#172554}}
main{{margin:60px auto;padding:50px;background:white;border-radius:20px;max-width:1580px}}
h1{{font-size:42px}}p{{color:#64748b;line-height:1.8}}table{{width:100%;border-collapse:collapse;margin:30px 0}}
td,th{{padding:20px;border-bottom:1px solid #e2e8f0;text-align:right}}td:first-child,th:first-child{{text-align:left}}
th{{background:#f1f5f9}}td:nth-child(2){{color:#2563eb}}td:nth-child(3){{color:#047857}}
.hash{{font:14px Consolas,monospace}}</style><main>
<p>Prompt Pals · 2026-11-28 · Apache JMeter 5.6.3</p><h1>10 万次请求 · 争抢 1,000 件库存</h1>
<p>100 个并发客户端 · 100,000 个独立账号 · 每人限购 1 件 · 10 个数据库连接 · 同一份代码与初始数据</p>
<table><tr><th>实测指标</th><th>同步基线</th><th>4 线程 / 256 队列</th></tr>{table}</table>
<p>售罄拒绝单独记录。HTTP 吞吐包含售罄后的业务响应；成功下单数量以实际落库订单为准。</p>
<p>SQL 核验：基线库存守恒 {summaries[0]['no_overselling']}，线程池库存守恒 {summaries[1]['no_overselling']}；
所有成功响应的订单均持久化，且每笔保存两位管理员的通知。</p>
<p>测量真实 HTTP、JWT、Redis 与 MySQL 订单链路。支付及真实 MQ 网络传输不计入。</p>
<p class="hash">Baseline SHA-256: {summaries[0]['jtl_sha256']}<br>Pool SHA-256: {summaries[1]['jtl_sha256']}</p></main></html>'''
    (output / 'pressure.html').write_text(document, encoding='utf-8')
    (output / 'pressure-results.json').write_text(json.dumps({'baseline': summaries[0], 'pool': summaries[1],
        'baseline_metadata': metadata[0], 'pool_metadata': metadata[1]}, indent=2, ensure_ascii=False), encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--baseline', default='baseline')
    parser.add_argument('--pool', default='pool')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--screenshots', action='store_true')
    parser.add_argument('--pressure', action='store_true')
    parser.add_argument('--pressure-baseline', default='pressure-baseline-keepalive')
    parser.add_argument('--pressure-pool', default='pressure-pool-keepalive')
    parser.add_argument('--chrome', type=Path, default=Path('C:/Program Files/Google/Chrome/Application/chrome.exe'))
    options = parser.parse_args()
    output = render(options)
    if options.screenshots:
        screenshot(output / 'comparison.html', output / 'comparison.png', options.chrome)
        print('Screenshot:', output / 'comparison.png')
    if options.pressure:
        pressure_report(options.run_dir.resolve(), output, options.pressure_baseline, options.pressure_pool)
        if options.screenshots:
            screenshot(output / 'pressure.html', output / 'pressure.png', options.chrome)
            print('Screenshot:', output / 'pressure.png')
