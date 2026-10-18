# CLAUDE.md

本文件是 Claude Code 在本仓库工作时的项目指引；项目约定变化时同步更新。

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

本地配置：各端 `.env` 均不入库（模板为 `.env.example`，新克隆后复制填写）；后端数据库连接写在 `api/.env`，由 `application.yaml` 的 `spring.config.import` 读入。

## 技术栈

- 后端：Java 21 · Spring Boot 4.1 · Maven（已含 Web MVC / Validation）；规划接入 MyBatis-Plus、MySQL、Redis、RustFS（S3 兼容对象存储）。
- 前端：Vue 3 · TypeScript · Vite · Pinia · Vue Router。
- 智能体（规划）：Python · FastAPI · LangGraph · Milvus。

## 数据模型（规划）

- 业务库 6 表：用户 / 商品 / 订单主表 + 订单明细 / 购物车 / 支付流水。
- AI 域独立库 `mall_ai`：会话消息 / 知识库 / 反馈与 QA 对 / 售后工单 / 问数审计、权限、指标字典。

## 开发优先级

登录权限细节 > 管理端页面＞核心业务与 AI 链路 。
