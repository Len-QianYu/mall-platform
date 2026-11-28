# 秒杀压测

使用真实 HTTP、生产 RS256 JWT 编码与账号校验、Redisson、Redis Lua、MySQL 事务和订单通知。每个请求使用独立账号和请求号，只有返回订单 ID 并通过独立 SQL 核验的请求计为成功下单。

本工具创建随机命名的 `mall_seckill_bench_*` 数据库，并以所有权标记限制清理范围；保留 100,000 个普通账号及两个管理员。使用独立 Redis DB 15、HTTP 端口 8086。生产开发库不参与压测。支付和真实 MQ 网络传输不在本次测量范围；两种模式均使用相同的 MQ 发送桩，不消费开发环境队列。

基线是 Tomcat 同步执行下单，实验模式在异步 HTTP 响应前将任务提交给有界业务线程池。两轮使用相同源代码、数据、索引、10 个 Druid 连接、200 个 Tomcat 最大线程及 512 MiB 最大堆。普通订单继续同步执行。原始系统的连接池耗尽故障另存为诊断记录，不将事务修复或索引收益算作线程池收益。

## 准备

在 `api/` 目录执行。需要 Java 21、MySQL、Redis、JMeter 5.6.3，以及 Python 的 `pymysql` 和 `psutil`。

```powershell
mvn.cmd -DskipTests compile dependency:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/seckill-benchmark-classpath.txt"
python bench/seckill/benchmark_data.py --run-dir bench/results/seckill/my-run --users 100000
```

本次环境使用已有 Maven 缓存，构建时额外传入 `-o -s target/payment-maven-settings.xml`。其他环境使用自己的 Maven 仓库配置即可。

## 同条件对比

每个命令都先重建本次测试库中的订单、活动和商品数据，重新签发账号令牌并预热；先前的原始记录保留在对应目录。不要同时运行两种模式。默认 JMeter 目录为 `C:/Jmeter/apache-jmeter-5.6.3`，可通过 `--jmeter-home` 修改。

```powershell
# 持续落库：每轮 10,000 个独立账号、10,000 次请求、10,000 件配额。
python bench/seckill/run_benchmark.py --run-dir bench/results/seckill/my-run --name baseline --requests 10000 --warmup 2000 --threads 100
python bench/seckill/run_benchmark.py --run-dir bench/results/seckill/my-run --name pool --requests 10000 --warmup 2000 --threads 100 --pool --workers 4 --queue 256

# 抢购：100,000 个独立账号争抢 1,000 件配额。售罄拒绝单独统计，不计为成功下单。
python bench/seckill/run_benchmark.py --run-dir bench/results/seckill/my-run --name pressure-baseline --requests 100000 --quota 1000 --warmup 2000 --threads 100
python bench/seckill/run_benchmark.py --run-dir bench/results/seckill/my-run --name pressure-pool --requests 100000 --quota 1000 --warmup 2000 --threads 100 --pool --workers 4 --queue 256
```

每轮保存 JTL、不可变的 JMeter 计划副本、控制台日志、JVM 日志、每秒资源记录、源码摘要、启动参数和 SQL 核验结果。HTTP 连接持续复用，每条请求的 JWT 和请求号仍不同；防止每次迭代重建连接耗尽本机临时端口。账号 JWT 文件属于临时测试凭据，位于已被 Git 忽略的 `bench/results/` 中。

报告包含请求总吞吐、成功落库吞吐、P95/P99、预期售罄拒绝、非预期失败、库存守恒、购买限额、订单 ID 与数据库对应关系、管理员通知数量及原始 JTL 的 SHA-256。

## 报告和截图

```powershell
python bench/seckill/build_report.py --run-dir bench/results/seckill/my-run --output ../docs/performance/seckill-report --screenshots
```

完整导出两种场景、四份官方 JMeter 截图和简历文案：

```powershell
python bench/seckill/finalize_report.py --run-dir bench/results/seckill/my-run --output ../docs/performance/seckill-report --baseline baseline --pool pool --pressure-baseline pressure-baseline --pressure-pool pressure-pool
```

截图由真实 Chrome 打开数据报告后生成，保留可查看的 HTML 与 JSON。原始 JMeter 报告可从各轮 `measured.jtl` 生成：

```powershell
java -jar C:/Jmeter/apache-jmeter-5.6.3/bin/ApacheJMeter.jar -g bench/results/seckill/my-run/baseline/measured.jtl -o bench/results/seckill/my-run/baseline/dashboard
```

## 回归

真实数据库回归使用自己创建的临时用户、商品、活动、请求，结束后清理对应数据。

```powershell
$env:TRADE_CONNECTION_POOL_TESTS = 'true'
$env:SECKILL_INTEGRATION_TESTS = 'true'
$env:ORDER_IDEMPOTENCY_INTEGRATION_TESTS = 'true'
mvn.cmd test
```

生产开关为 `SECKILL_EXECUTOR_ENABLED`；工作线程数、队列容量、等待超时分别通过 `SECKILL_EXECUTOR_WORKERS`、`SECKILL_EXECUTOR_QUEUE_CAPACITY`、`SECKILL_EXECUTOR_TIMEOUT` 配置。超时重试必须复用原 `requestId`，已有幂等与库存事务继续生效。
