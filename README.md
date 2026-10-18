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
- **数据与基础设施（规划中）**：MyBatis-Plus · MySQL · Redis · RustFS（S3 兼容对象存储）
- **智能体（规划中）**：Python · FastAPI · LangGraph · Milvus

## 快速开始

### 环境要求

- JDK 21+
- Node.js ≥ 22.18
- Maven 3.9+

### 后端（api/）

```bash
cd api
./mvnw spring-boot:run        # Windows 下使用 mvnw.cmd spring-boot:run
```

服务默认端口 **8080**。
打包命令：`./mvnw clean package`。

### 前端（web/）

```bash
cd web
npm install
npm run dev        # 开发服务器，默认端口 5173
```

打包命令：`npm run build`（类型检查 + 生产构建）

## Roadmap

- 前后端工程骨架（Spring Boot 4.1 + Vue 3 + Vite）
- 数据库设计：业务 6 表（用户 / 商品 / 订单主表+明细 / 购物车 / 支付流水）+ AI 域（会话 / 知识库 / 反馈）
- 主站核心业务：登录鉴权、商品浏览、下单与库存
- 基础设施接入：MySQL / Redis / RustFS（docker-compose 一键启动）
- 面向智能体的工具接口：订单 / 库存 / 售后查询（供客服 Agent 调用）
- 面向问数的接口：元数据服务 + 受权限控制的查询执行
- 接入 Python 智能体服务：SSE 流式对话与问数
