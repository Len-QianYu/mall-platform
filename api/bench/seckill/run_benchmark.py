"""Run the same real HTTP workload against synchronous and bounded-pool variants."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import threading
import time
import urllib.request

import psutil

from benchmark_data import API, activity, audit, connect, owned_database, reset, settings

JAVA = str(Path(shutil.which('jcmd')).with_name('java.exe')) if os.name == 'nt' and shutil.which('jcmd') else shutil.which('java')
JAVAC = str(Path(JAVA).with_name('javac.exe')) if os.name == 'nt' else shutil.which('javac')


def run_jmeter(options, mode_dir, accounts, phase, product, campaign, requests, threads):
    if requests % threads:
        raise ValueError('Requests must be a multiple of concurrency so each run has exactly the specified sample count.')
    jmeter_home = Path(options.jmeter_home)
    jtl = mode_dir / f'{phase}.jtl'
    plan = mode_dir / f'{phase}-plan.jmx'
    shutil.copyfile(Path(__file__).with_name('seckill.jmx'), plan)
    command = [JAVA, '-Xms128m', '-Xmx512m', '-Duser.timezone=Asia/Shanghai',
               '-jar', str(jmeter_home / 'bin/ApacheJMeter.jar'), '-n',
               '-t', str(plan),
               f'-Jport={options.port}', f'-Jaccounts={accounts}', f'-Jphase={phase}',
               f'-Jproduct={product}', f'-Jactivity={campaign}', f'-Jthreads={threads}',
               f'-Jloops={requests // threads}', '-Jrampup=5', '-Jsummariser.interval=15',
               '-Jsample_variables=userId,orderId,failureKind', '-Jjmeter.save.saveservice.timestamp_format=ms',
               '-Jjmeter.save.saveservice.output_format=csv', '-Jjmeter.save.saveservice.autoflush=true',
               '-l', str(jtl), '-j', str(mode_dir / f'{phase}-jmeter.log')]
    (mode_dir / f'{phase}-command.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
    print(f'START {phase}: {requests:,} requests, {threads} concurrent clients.', flush=True)
    with (mode_dir / f'{phase}-console.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=API, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace',
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        try:
            for line in process.stdout:
                log.write(line)
                log.flush()
                if 'summary' in line.lower() or line.startswith('ERROR') or 'end of run' in line.lower():
                    print(line.rstrip(), flush=True)
            if process.wait() != 0:
                raise RuntimeError(f'JMeter failed; see {phase}-console.log.')
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
    result = audit(options.database, campaign, jtl, mode_dir / f'{phase}-summary.json')
    if result['requests'] != requests:
        raise RuntimeError('The recorded request count does not match the requested workload.')
    if not result['no_overselling'] or not result['all_response_orders_persisted']:
        raise RuntimeError('Stock or response/database consistency check failed.')
    return result


def monitor(stop, process, output):
    fields = ['epoch_ms', 'server_cpu_percent', 'server_rss_mib', 'server_threads',
              'machine_cpu_percent', 'available_memory_mib']
    with output.open('w', encoding='utf-8', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=fields)
        writer.writeheader()
        monitored = psutil.Process(process.pid)
        monitored.cpu_percent()
        while not stop.wait(1):
            try:
                writer.writerow({'epoch_ms': int(time.time() * 1000), 'server_cpu_percent': monitored.cpu_percent(),
                                 'server_rss_mib': round(monitored.memory_info().rss / 2**20, 2),
                                 'server_threads': monitored.num_threads(), 'machine_cpu_percent': psutil.cpu_percent(),
                                 'available_memory_mib': round(psutil.virtual_memory().available / 2**20, 2)})
                file.flush()
            except (psutil.Error, OSError):
                return


def main(options):
    run_dir = options.run_dir.resolve()
    config = json.loads((run_dir / 'run.json').read_text(encoding='utf-8'))
    options.database = config['database']
    owned_database(options.database)
    mode_dir = run_dir / options.name
    mode_dir.mkdir(exist_ok=False)
    reset(options.database)
    accounts = mode_dir / 'accounts.csv'
    classpath_file = API / 'target/seckill-benchmark-classpath.txt'
    classpath = classpath_file.read_text(encoding='utf-8').strip() + os.pathsep + str(API / 'target/classes')
    tools_dir = API / 'target/seckill-benchmark-tools'
    tools_dir.mkdir(exist_ok=True)
    subprocess.run([JAVAC, '-proc:none', '--class-path', classpath, '-d', str(tools_dir),
                    str(API / 'tools/SeckillBenchmarkServer.java')], check=True, cwd=API)
    classpath += os.pathsep + str(tools_dir)
    command = [JAVA, '-Xms128m', '-Xmx512m', '-XX:+UseG1GC',
               '-Dspring.devtools.restart.enabled=false',
               f'-Xlog:gc:file={mode_dir / "gc.log"}', '--class-path', classpath,
               'SeckillBenchmarkServer',
               f'--DB_NAME={options.database}', f'--REDIS_DATABASE={config["redis_database"]}',
               f'--server.port={options.port}', '--server.address=127.0.0.1',
               '--server.tomcat.threads.max=200', '--server.tomcat.threads.min-spare=20',
               '--spring.datasource.druid.initial-size=10', '--spring.datasource.druid.min-idle=10',
               '--spring.datasource.druid.max-active=10', '--product-notification.delivery-enabled=false',
               '--seckill.expiry-enabled=false', '--payment.reconcile-enabled=false', '--alipay.enabled=false',
               '--spring.devtools.restart.enabled=false',
               '--spring.devtools.add-properties=false', '--logging.level.root=WARN',
               '--logging.level.org.springframework=WARN', '--logging.level.com.example.api=WARN',
               '--logging.level.org.springframework.web=WARN', '--logging.level.org.springframework.boot=WARN',
               '--logging.level.org.springframework.security=WARN',
               f'--seckill.executor.enabled={str(options.pool).lower()}',
               f'--seckill.executor.workers={options.workers}', f'--seckill.executor.queue-capacity={options.queue}',
               f'--benchmark.csv={accounts}', f'--benchmark.accounts={max(options.requests, options.warmup)}']
    (mode_dir / 'server-command.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
    with connect(options.database) as db, db.cursor() as cursor:
        cursor.execute('SHOW INDEX FROM user')
        indexes = sorted({row[2] for row in cursor.fetchall()})
    source_hash = hashlib.sha256()
    for source in sorted((API / 'src/main').rglob('*')):
        if source.is_file() and source.suffix in ['.java', '.yaml', '.sql', '.lua']:
            source_hash.update(str(source.relative_to(API)).encode())
            source_hash.update(source.read_bytes())
    metadata = {'variant': 'bounded-business-pool' if options.pool else 'servlet-synchronous',
                'workers': options.workers if options.pool else None, 'queue_capacity': options.queue if options.pool else None,
                'requests': options.requests, 'stock_quota': options.quota or options.requests,
                'concurrency': options.threads, 'db_connections': 10, 'source_sha256': source_hash.hexdigest(),
                'heap_max_mib': 512, 'tomcat_max_threads': 200, 'user_indexes': indexes,
                'http_connection_reuse': True,
                'jmx_sha256': hashlib.sha256(Path(__file__).with_name('seckill.jmx').read_bytes()).hexdigest(),
                'scope': config['scope'], 'source_commit': subprocess.check_output(
                    ['git', 'rev-parse', 'HEAD'], cwd=API, text=True).strip()}
    (mode_dir / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(f'Starting isolated {metadata["variant"]} server and signing {max(options.requests, options.warmup):,} account tokens.', flush=True)
    with (mode_dir / 'server.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=API, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        stop = threading.Event()
        sampler = threading.Thread(target=monitor, args=(stop, process, mode_dir / 'resources.csv'), daemon=True)
        sampler.start()
        try:
            deadline = time.monotonic() + 600
            while not (mode_dir / 'ready.json').exists():
                if process.poll() is not None:
                    raise RuntimeError('Benchmark server failed to start; inspect server.log.')
                if time.monotonic() > deadline:
                    raise TimeoutError('Account token preparation exceeded ten minutes.')
                time.sleep(2)
            print('Signed account fixtures ready. Starting warmup.', flush=True)
            if json.loads((mode_dir / 'ready.json').read_text())['pid'] != process.pid:
                raise RuntimeError('Java launcher PID differs from the JVM PID; use the JDK executable directly.')
            product, campaign = activity(options.database, options.name + '-warmup', options.warmup)
            warmup = run_jmeter(options, mode_dir, accounts, 'warmup', product, campaign, options.warmup, 20)
            if warmup['successes'] != options.warmup:
                raise RuntimeError('Warmup had business/HTTP failures; refusing to publish an invalid performance run.')
            product, campaign = activity(options.database, options.name + '-measured', options.quota or options.requests)
            result = run_jmeter(options, mode_dir, accounts, 'measured', product, campaign, options.requests, options.threads)
            (mode_dir / 'summary.json').write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
            print(f'COMPLETE {options.name}: {result["committed_orders_per_second"]} committed orders/s, P95 {result["p95_ms"]} ms.', flush=True)
        finally:
            stop.set()
            sampler.join(timeout=3)
            # Only this tool's child process is stopped. Development services stay running.
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--pool', action='store_true')
    parser.add_argument('--threads', type=int, default=100)
    parser.add_argument('--requests', type=int, default=100000)
    parser.add_argument('--warmup', type=int, default=3000)
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--queue', type=int, default=256)
    parser.add_argument('--quota', type=int)
    parser.add_argument('--port', type=int, default=8086)
    parser.add_argument('--jmeter-home', default=os.environ.get('JMETER_HOME', 'C:/Jmeter/apache-jmeter-5.6.3'))
    main(parser.parse_args())
