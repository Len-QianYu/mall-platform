"""Synthetic data and independent database audits; never mutates the development database."""
import argparse
import csv
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import statistics

import psutil
import pymysql

API = Path(__file__).resolve().parents[2]


def settings():
    values = {}
    for line in (API / '.env').read_text(encoding='utf-8-sig').splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    values.update({key: value for key, value in os.environ.items() if key.startswith('DB_')})
    return values


def connect(database=None):
    config = settings()
    return pymysql.connect(host=config.get('DB_HOST', 'localhost'), port=int(config.get('DB_PORT', '3306')),
                           user=config.get('DB_USERNAME', 'root'), password=config.get('DB_PASSWORD', ''),
                           database=database, charset='utf8mb4', autocommit=True)


def owned_database(database):
    if not re.fullmatch(r'mall_seckill_bench_[a-f0-9]{12}', database):
        raise ValueError('Only this tool\'s randomly named benchmark databases are allowed.')
    with connect(database) as db, db.cursor() as cursor:
        cursor.execute('SELECT database_name FROM benchmark_manifest LIMIT 1')
        if cursor.fetchone() != (database,):
            raise ValueError('Benchmark ownership marker does not match.')


def sql_statements(source):
    quote = None
    start = 0
    escaped = False
    for position, char in enumerate(source):
        if escaped:
            escaped = False
        elif char == '\\' and quote:
            escaped = True
        elif quote:
            if char == quote:
                quote = None
        elif char in "'\"`":
            quote = char
        elif char == ';':
            yield source[start:position].strip()
            start = position + 1
    if source[start:].strip():
        yield source[start:].strip()


def create(run_dir, count):
    run_dir.mkdir(parents=True, exist_ok=False)
    database = 'mall_seckill_bench_' + secrets.token_hex(6)
    schema = (API / 'src/main/resources/db/schema.sql').read_text(encoding='utf-8')
    schema = re.sub(r'/\*.*?\*/|--[^\n]*', '', schema, flags=re.S)
    with connect() as db, db.cursor() as cursor:
        cursor.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4')
    print('Creating isolated database:', database, flush=True)
    with connect(database) as db, db.cursor() as cursor:
        cursor.execute('CREATE TABLE benchmark_manifest (database_name VARCHAR(64) PRIMARY KEY)')
        cursor.execute('INSERT INTO benchmark_manifest VALUES (%s)', (database,))
        for statement in sql_statements(schema):
            if statement and not re.match(r'(CREATE DATABASE|USE)\b', statement, re.I):
                cursor.execute(statement)
        # A published BCrypt example hash, used only by synthetic, isolated accounts.
        password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
        for start in range(1, count + 1, 1000):
            now = dt.datetime.now()
            rows = [(i, f'bench{i:06d}', f'Bench user {i}', password, 'user', 1, now)
                    for i in range(start, min(start + 1000, count + 1))]
            cursor.executemany('INSERT INTO user (id,username,name,password,role,status,registered_at) '
                               'VALUES (%s,%s,%s,%s,%s,%s,%s)', rows)
        cursor.executemany('INSERT INTO user (id,username,name,password,role,status,registered_at) '
                           'VALUES (%s,%s,%s,%s,%s,1,NOW())', [
                               (count + 1, 'benchadmin1', 'Benchmark admin', password, 'admin'),
                               (count + 2, 'benchadmin2', 'Benchmark super admin', password, 'super_admin')])
    config = {'database': database, 'users': count, 'redis_database': 15, 'port': 8086,
              'client_timezone': 'Asia/Shanghai', 'created_at': dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
              'logical_cpus': psutil.cpu_count(), 'physical_cpus': psutil.cpu_count(logical=False),
              'ram_gib': round(psutil.virtual_memory().total / 2**30, 2),
              'cpu_identifier': os.environ.get('PROCESSOR_IDENTIFIER'),
              'scope': 'Real HTTP + production RS256/account validation + Redis Lua/Redisson + MySQL committed orders; MQ transport stubbed equally; payment excluded.'}
    (run_dir / 'run.json').write_text(json.dumps(config, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps({'database': database, 'users': count, 'run_dir': str(run_dir)}, ensure_ascii=False), flush=True)


def reset(database):
    owned_database(database)
    with connect(database) as db, db.cursor() as cursor:
        for table in ['order_notification', 'order_shipment_notification', 'order_item', 'payment', 'orders',
                      'seckill_purchase', 'seckill_activity', 'product']:
            cursor.execute(f'TRUNCATE TABLE `{table}`')


def activity(database, title, quota):
    owned_database(database)
    with connect(database) as db, db.cursor() as cursor:
        cursor.execute('INSERT INTO product (name,category,price,stock,status) VALUES (%s,%s,99,0,1)',
                       (title, 'Benchmark'))
        product = cursor.lastrowid
        cursor.execute('INSERT INTO seckill_activity (product_id,title,original_price,seckill_price,total_stock,stock,'
                       'returned_stock,limit_per_user,starts_at,ends_at,status) '
                       'VALUES (%s,%s,99,9.9,%s,%s,0,1,NOW()-INTERVAL 1 HOUR,NOW()+INTERVAL 1 DAY,1)',
                       (product, title, quota, quota))
        return product, cursor.lastrowid


def percentile(values, percent):
    ordered = sorted(values)
    position = (len(ordered) - 1) * percent / 100
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def audit(database, activity_id, jtl, output):
    owned_database(database)
    with jtl.open(encoding='utf-8-sig', newline='') as file:
        rows = list(csv.DictReader(file))
    if not rows:
        raise ValueError('JMeter did not record any requests.')
    elapsed = [int(row['elapsed']) for row in rows]
    successes = [row for row in rows if row['success'].lower() == 'true']
    start = min(int(row['timeStamp']) for row in rows)
    end = max(int(row['timeStamp']) + int(row['elapsed']) for row in rows)
    duration = (end - start) / 1000
    from collections import Counter
    failures = Counter(row.get('failureKind') or row['responseCode'] for row in rows if row['success'].lower() != 'true')
    expected_stock_rejections = sum(count for reason, count in failures.items() if '抢完' in reason or '配额不足' in reason)
    with connect(database) as db, db.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute('SELECT total_stock,stock,returned_stock,limit_per_user FROM seckill_activity WHERE id=%s', (activity_id,))
        stock = cursor.fetchone()
        cursor.execute('SELECT COUNT(*) orders, COUNT(DISTINCT o.user_id) buyers, SUM(i.quantity) quantity '
                       'FROM orders o JOIN order_item i ON i.order_id=o.id WHERE i.seckill_activity_id=%s', (activity_id,))
        orders = cursor.fetchone()
        cursor.execute('SELECT COUNT(*) over_limit FROM seckill_purchase WHERE activity_id=%s AND quantity>1', (activity_id,))
        over_limit = cursor.fetchone()['over_limit']
        cursor.execute('SELECT COUNT(*) notifications FROM order_notification n JOIN order_item i ON i.order_id=n.order_id '
                       'WHERE i.seckill_activity_id=%s', (activity_id,))
        notifications = cursor.fetchone()['notifications']
        cursor.execute('SELECT VERSION() version, @@innodb_flush_log_at_trx_commit durability, @@transaction_isolation isolation')
        mysql = cursor.fetchone()
        cursor.execute('SELECT o.id FROM orders o JOIN order_item i ON i.order_id=o.id WHERE i.seckill_activity_id=%s', (activity_id,))
        stored_ids = {str(row['id']) for row in cursor.fetchall()}
    response_ids = {row['orderId'] for row in successes}
    quantity = int(orders['quantity'] or 0)
    result = {'requests': len(rows), 'successes': len(successes), 'failures': len(rows) - len(successes),
              'duration_seconds': round(duration, 3), 'completed_requests_per_second': round(len(rows) / duration, 2),
              'committed_orders_per_second': round(len(successes) / duration, 2),
              'mean_ms': round(statistics.mean(elapsed), 2), 'p50_ms': percentile(elapsed, 50),
              'p95_ms': percentile(elapsed, 95), 'p99_ms': percentile(elapsed, 99), 'max_ms': max(elapsed),
              'error_percent': round((len(rows) - len(successes)) / len(rows) * 100, 4),
              'expected_stock_rejections': expected_stock_rejections,
              'unexpected_failures': len(rows) - len(successes) - expected_stock_rejections,
              'stock': stock, 'database_orders': orders['orders'], 'distinct_buyers': orders['buyers'],
              'purchased_quantity': quantity, 'over_limit_buyers': over_limit, 'admin_notifications': notifications,
              'no_overselling': stock['total_stock'] == stock['stock'] + stock['returned_stock'] + quantity,
              'all_response_orders_persisted': response_ids == stored_ids and len(response_ids) == len(successes),
              'notifications_complete': notifications == orders['orders'] * 2,
              'failure_reasons': dict(failures), 'mysql': mysql,
              'jtl_sha256': hashlib.file_digest(jtl.open('rb'), 'sha256').hexdigest(),
              'sample_start_epoch_ms': start, 'sample_end_epoch_ms': end}
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--users', type=int, default=100000)
    options = parser.parse_args()
    create(options.run_dir.resolve(), options.users)
