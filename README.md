# Mall Platform（电商平台）

个人全栈项目：**Java 后端 + Vue 3 前端**的电商平台，并接入 Python 智能体服务（电商智能客服、电商问数 Text2SQL），为其提供业务数据与工具接口。

## 项目结构

| 目录 | 说明 | 状态 |
|---|---|---|
| [`api/`](api/) | Java 后端：平台业务 API、面向 AI 智能体的工具接口 | 骨架已就绪 |
| [`web/`](web/) | Vue 3 前端：商城前台 + 管理端 | 骨架已就绪 |
| `agent/` | Python 智能体服务：智能客服 / 问数） | 规划中 |

## 技术栈

- **后端**：Java 21 · Spring Boot 4.1 · Maven（已引入 Web MVC / Validation 起步依赖）
- **前端**：Vue 3 · TypeScript · Vite · Pinia · Vue Router
- **数据与基础设施（规划中）**：MyBatis-Plus · MySQL · Druid（连接池）· Redis · RocketMQ（消息队列）· RustFS（S3 兼容对象存储）
- **智能体（规划中）**：Python · FastAPI · LangGraph · Milvus

## 快速开始

### 环境要求

- JDK 21+
- Node.js ≥ 22.18
- Maven 3.9+
- MySQL 8（本机安装）
- Docker Desktop（运行 Redis、RocketMQ、RustFS）

### 本地依赖（Redis + RocketMQ + RustFS）

```bash
docker compose up -d     # 仓库根目录执行：Redis 8.10 + RocketMQ 5.5 + RustFS 1.0
docker compose stop      # 停止
```

端口：Redis **6379**、RocketMQ NameServer **9876** / Broker **10911** / Proxy gRPC **8081**、RustFS S3 API **9000** / 控制台 **9001**（`/rustfs/console/`），均只绑定 127.0.0.1。更多命令见 [`docker-compose.yml`](docker-compose.yml) 顶部注释。

### 后端（api/）

```bash
cd api
./mvnw spring-boot:run        # Windows 下使用 mvnw.cmd spring-boot:run
```

服务默认端口 **8080**。
数据库连接池监控（Druid）：<http://127.0.0.1:8080/druid/>，只允许本机访问，默认账号 druid / druid123（可在 `api/.env` 里改）。
打包命令：`./mvnw clean package`。

### 前端（web/）

```bash
cd web
npm install
npm run dev        # 开发服务器，默认端口 5173
```

打包命令：`npm run build`（类型检查 + 生产构建）

## 性能压测

商品接口用 JMeter 压测，脚本和一键运行脚本在 [`api/bench/`](api/bench/)（`run.cmd <结果名> [并发数] [秒数]`，先 10 并发预热 10 秒再正式压测）。

### 基线（未加缓存：MySQL + Druid 连接池）

- 数据量：商品表 **10 万条**（`api/src/main/resources/db/bench-product.sql` 生成）
- 压测方式：5 个场景**依次**执行（线程组串行），每个场景 **50 并发、持续 60 秒**，ramp-up 5 秒
- 环境：本机单机（后端、MySQL、JMeter 同一台 Windows 电脑），结果仅作优化前后对比参考

| 场景 | 接口 | 请求数 | 吞吐量 (req/s) | 平均 (ms) | P90 (ms) | P99 (ms) | 错误率 |
|---|---|---:|---:|---:|---:|---:|---:|
| 分类列表 | `GET /product/categories` | 226,906 | 3780.3 | 12.5 | 27 | 51 | 0% |
| 商品详情 | `GET /product/{id}`（随机 id） | 204,383 | 3405.4 | 13.9 | 31 | 58 | 0% |
| 商品列表-默认排序 | `GET /product?page=1~10&pageSize=20` | 6,653 | 110.6 | 434.0 | 505 | 641 | 0% |
| 商品搜索-关键字 | `GET /product?keyword=...` | 4,655 | 77.2 | 618.7 | 707 | 945 | 0% |
| 商品列表-销量排序 | `GET /product?sort=sales` | 1,469 | 23.8 | 1993.5 | 2156 | 2223 | 0% |

**结论**：按主键查询的详情和分类接口很快；瓶颈在分页列表、关键字搜索和销量排序——10 万行上的排序/模糊匹配加 COUNT 统计让单次请求达到数百毫秒到 2 秒。下一步：接入 Redis 缓存热点列表与详情，并针对排序/搜索做索引优化，再用同一脚本复测对比。

<details>
<summary>压测截图（JMeter 报告 / Druid 监控）</summary>

![JMeter 统计](docs/bench/baseline/jmeter-statistics.png)
![吞吐量](docs/bench/baseline/jmeter-TransactionsPerSecond.png)
![响应时间](docs/bench/baseline/jmeter-ResponseTimesOverTime.png)
![响应时间百分位](docs/bench/baseline/jmeter-ResponseTimesPercentiles.png)
![Druid 数据源](docs/bench/baseline/druid-datasource.png)
![Druid SQL 监控](docs/bench/baseline/druid-sql.png)
![Druid URI 监控](docs/bench/baseline/druid-weburi.png)

</details>

## Roadmap

- 前后端工程骨架（Spring Boot 4.1 + Vue 3 + Vite）
- 数据库设计：业务 6 表（用户 / 商品 / 订单主表+明细 / 购物车 / 支付流水）+ AI 域（会话 / 知识库 / 反馈）
- 主站核心业务：登录鉴权、商品浏览、下单与库存
- 基础设施接入：MySQL / Redis / RocketMQ / RustFS（docker-compose 一键启动）
- 面向智能体的工具接口：订单 / 库存 / 售后查询（供客服 Agent 调用）
- 面向问数的接口：元数据服务 + 受权限控制的查询执行
- 接入 Python 智能体服务：SSE 流式对话与问数
