AGENT.md

本文件是 AI 编程助手在本仓库工作时的项目指引；项目约定变化时与 `CLAUDE.md` 同步更新。

## 项目定位

Mall Platform：个人作品集项目（AI 全栈方向）——电商平台，Java 后端 + Vue 3 前端 + Python 智能体。代码以「可运行、能讲清设计取舍」为标准。

## 目录结构

| 目录 | 说明 | 状态 |
|---|---|---|
| `api/` | Java 后端：平台业务 API + 面向智能体的工具接口 | 骨架就绪 |
| `web/` | Vue 3 前端：商城前台 + 管理端 | 骨架就绪 |
| `agent/` | Python 智能体：一个 LangGraph 应用，入口 router + 客服/问数两个子图 | 规划中 |

## 架构约定（重要）

- **Java（api/）是唯一数据出口**：Python 智能体不直连数据库，所有数据访问经 Java 接口，由 Java 收口权限、审计、限流；问数的 SQL 也由 Java 执行，不交给 Python。
- 智能体 ↔ api/ 走 HTTP；对话流式输出用 SSE。
- 单体架构，不引入微服务组件，架构不照搬。

## 常用命令

后端 `api/`（Git Bash 用 `./mvnw`，CMD / PowerShell 用 `mvnw.cmd`）：

```bash
cd api
./mvnw spring-boot:run    # 启动，端口 8080
./mvnw test               # 测试
./mvnw clean package      # 打包
```

前端 `web/`（Node `^22.19.0 || >=24.12.0`）：

```bash
cd web
npm run dev          # 开发服务器，端口 5173
npm run build        # 类型检查 + 生产构建
npm run test:unit    # 单元测试（Vitest）
npm run lint         # oxlint + eslint
npm run format       # prettier
```

本地依赖（仓库根目录，需先启动 Docker Desktop；MySQL 用本机安装的）：

```bash
docker compose up -d     # 启动 Redis 8.10（6379）+ RocketMQ 5.5（NameServer 9876 / Broker 10911 / Proxy gRPC 8081）+ RustFS 1.0（S3 API 9000 / 控制台 9001）
docker compose stop      # 停止；更多命令见 docker-compose.yml 顶部注释
```

后端依赖 RocketMQ（订单支付超时取消）：启动后端前要先 `docker compose up -d`，新环境还要按 `docker-compose.yml` 顶部注释建一次 Topic `order-pay-timeout`（DELAY 类型）和消费组 `order-pay-timeout-consumer`。

多个后端实例用同一个消费组会分摊消息：连测试库的实例不要和连真实库的实例同时运行，否则消息可能被另一边消费。

本地配置：各端 `.env` 均不入库（模板为 `.env.example`，新克隆后复制填写）；后端数据库连接写在 `api/.env`，由 `application.yaml` 的 `spring.config.import` 读入。

## 技术栈

- 后端：Java 21 · Spring Boot 4.1 · Maven（已含 Web MVC / Validation）；已接入 MyBatis-Plus、MySQL、Redis、RocketMQ（消息队列，不用 Kafka；Java 端用 rocketmq-client-java 5.x 经 Proxy gRPC 连接，不用与 Boot 4 不配套的 rocketmq-spring-boot-starter）、RustFS（S3 兼容对象存储）。连接池已换成 Druid（Boot 4 专用的 druid-spring-boot-4-starter，排除了 HikariCP），参数在 `application.yaml` 的 `spring.datasource.druid`；监控页 `/druid/`（只许本机访问 + 登录，Security 对它整段放行）。Redis 已用于前台商品缓存（Spring Cache 注解 + `CacheConfiguration`，写库后在事务提交后删缓存；管理端和下单流程读库不读缓存；Redis 出错只记日志、回退查库）。
- 前端：Vue 3 · TypeScript · Vite · Pinia · Vue Router。
- 文件存储已接入 RustFS（AWS S3 SDK 2.x + path-style），配置为 `rustfs.*` / `api/.env` 的 `RUSTFS_*`；首次上传自动创建私有桶，头像和商品图片仍使用 `/avatars/**`、`/images/**`，由 Java 后端流式读取。上传权限和 5MB 图片校验保持不变，文件服务不可用时返回 503。
- 支付已接入支付宝官方 SDK（默认沙箱），仍使用单体的订单与支付流水：收银台跳转、RSA2 回调验签、主动查询、每分钟按流水 ID 分页轮询对账、关闭交易和原路退款。对账逐笔处理被扫描的流水，查询失败也推进游标，轮询完再重试，避免旧流水阻塞后续支付。配置为 `alipay.*` / `api/.env` 的 `ALIPAY_*`，默认关闭；正式收款须关闭 `PAYMENT_MOCK_ENABLED`。只有 `POST /payment/alipay/notify` 免 JWT，其余支付接口校验订单归属。支付、取消、退款统一先锁订单行，再锁流水和商品；成功只累加一次销量，取消后的晚到收款自动退款。已有库升级脚本为 `api/src/main/resources/db/upgrade-alipay-payment.sql`，密钥只保存在被忽略的 `.env`。数据库测试禁用真实消息和对账任务并回滚写入。
- 智能体（规划）：Python · FastAPI · LangGraph · Milvus。
- 下单幂等：`POST /order/create` 必须携带 16～64 位 ASCII 请求号 `requestId`，同一用户同号同内容返回已有订单，同号不同内容拒绝。先查库，再获取 Redisson 锁，锁内重查后开启独立事务；提交或回滚完成后解锁，watchdog 自动续期。`orders.request_id` / `request_hash` 和 `(user_id, request_id)` 唯一索引随订单一起落库，已有库执行 `api/src/main/resources/db/upgrade-order-idempotency.sql`。Redisson 沿用 `spring.data.redis` 配置，Redis 不可用时新下单失败、已完成请求仍可查库返回；`ORDER_CREATE_LOCK_WAIT` 默认 3 秒。前端失败和刷新重试复用请求号，成功后再次购买生成新号。下单独立事务创建的测试订单、商品须在测试结束后清理；真实 Redis / MySQL 并发测试通过 `ORDER_IDEMPOTENCY_INTEGRATION_TESTS=true` 显式开启。

## 数据模型（规划）

- 业务库 6 表：用户 / 商品 / 订单主表 + 订单明细 / 购物车 / 支付流水。
- AI 域独立库 `mall_ai`：会话消息 / 知识库 / 反馈与 QA 对 / 售后工单 / 问数审计、权限、指标字典。

## 开发优先级

登录权限细节 > 管理端页面＞核心业务与 AI 链路 。

用户角色编码为 `user` / `admin` / `super_admin`，显示为用户 / 管理员 / super管理员。super管理员使用管理员登录入口并拥有管理端权限；前端保存后端返回的真实角色。现有“千羽”账号的角色升级 SQL 为 `api/src/main/resources/db/upgrade-super-admin.sql`。

登录页“记住账号”保存用户名和登录方式，自成功登录起 7 天有效；再次打开登录页时恢复，过期或取消勾选时清除。注册后跳转携带的用户名优先填入，并使用用户登录方式。
