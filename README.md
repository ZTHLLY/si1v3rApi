# si1v3rApi

一套简单的 API 开放平台示例：后端基于 Spring Boot + MyBatis-Plus，前端基于 Umi + Ant Design Pro，配有独立的客户端 SDK、Spring Cloud Gateway 鉴权网关，以及一个虚拟接口服务用来本地验证签名调用链路。

## 仓库结构

- `si1v3rApi-backend`：主站后端，提供用户、接口管理、发布/下线、调用统计等 API，默认端口 `8003`，上下文 `/api`。  
  - `sql/create_table.sql`：数据库表结构。  
  - `si1v3rApiClient-sdk`：Java 客户端 SDK，内置签名逻辑与示例调用。  
  - `virtualApi`：虚拟接口服务（端口 `8123`，上下文 `/api`），用于被网关转发调用。  
  - `si1v3rApi-gateway`：Spring Cloud Gateway 网关（端口 `8090`），包含 IP 白名单、签名校验、响应日志。
- `si1v3rApi-frontend`：前端控制台，支持接口列表、详情、在线调试、用户/管理员页等，默认运行在 `8000` 端口。

## 环境要求

- Java 8+、Maven（仓库附带 `mvnw` 可直接使用）
- Node.js 16+，建议 `yarn` 管理前端依赖
- MySQL：创建数据库并导入 `si1v3rApi-backend/sql/create_table.sql`
- 可选：Redis、Elasticsearch、COS、微信相关配置（在后端 `application.yml` 中按需开启）
- 如需 Dubbo/Nacos 注册中心，请准备好 Nacos（网关和主站的 Dubbo 默认指向 `localhost:8848`）

## 后端主服务（端口 8003）

1. **数据库准备**：创建名为 `si1v3rApi` 的库，执行 `si1v3rApi-backend/sql/create_table.sql`。
2. **修改配置**：编辑 `si1v3rApi-backend/src/main/resources/application.yml`，替换 MySQL 账号密码，按需开启 Redis/ES/COS/微信配置。
3. **安装 SDK 依赖**：先进入 `si1v3rApi-backend/si1v3rApiClient-sdk` 执行 `mvn clean install`，将本地 SDK 安装到 Maven 仓库。
4. **启动主站**：
   ```bash
   cd si1v3rApi-backend
   ./mvnw spring-boot:run
   ```
   运行后访问接口文档 `http://localhost:8003/api/doc.html`（Knife4j）。

## 虚拟接口服务（端口 8123）

- **作用**：提供简单的签名校验示例接口（`GET /api/`、`POST /api/name`、`POST /api/user`），用于网关转发和 SDK 调用链路调试。
- **配置**：`virtualApi/src/main/resources/application.yml` 中的 `si1v3r.client.assess-key/secret-key` 与 SDK 默认保持一致。
- **启动**：
  ```bash
  cd si1v3rApi-backend/virtualApi
  mvn spring-boot:run
  ```

## 网关（端口 8090）

- **作用**：Spring Cloud Gateway 统一入口，包含 IP 白名单校验（默认 `127.0.0.1`）、签名/时间戳验证、响应日志收集。
- **转发**：`src/main/resources/application.yml` 默认将 `/api/**` 转发到 `http://localhost:8123`（虚拟接口服务）。如需转发到其他后端，修改 `routes[0].uri` 与 `Path` 规则。
- **启动**：
  ```bash
  cd si1v3rApi-backend/si1v3rApi-gateway
  mvn spring-boot:run
  ```
  （如启用 Dubbo，请确保 Nacos 已就绪。）

## 客户端 SDK 使用

- **位置**：`si1v3rApi-backend/si1v3rApiClient-sdk`，提供签名工具和 `Si1v3rApiClient` 客户端。
- **样例**：
  ```java
  Si1v3rApiClient client = new Si1v3rApiClient("admin01", "abcdefgh");
  // GET 示例
  client.getNameByGet("si1v3r");
  // POST 表单示例
  client.getNameByPost("si1v3r");
  // JSON + 签名示例
  User user = new User();
  user.setName("tester");
  client.getUsernameByPost(user);
  ```
  客户端内部会生成 `assessKey/nonce/timestamp/sign` 头并调用网关 `http://localhost:8090/api/**`。

## 前端（端口 8000）

1. 安装依赖：
   ```bash
   cd si1v3rApi-frontend
   yarn
   ```
2. 启动开发环境（默认直连后端 `http://localhost:8003`，可在 `src/constants/index.ts` 修改）：
   ```bash
   yarn start:dev
   ```
   访问 `http://localhost:8000`。前端使用 Umi/Ant Design Pro，内置登录、接口列表、接口详情与在线调用、用户中心、管理员功能页等。

## 启动顺序建议

1) MySQL 初始化 -> 2) 安装 SDK -> 3) 启动后端主服务 -> 4) 启动虚拟接口服务 -> 5) 启动网关 -> 6) 启动前端。根据实际需要启用 Redis/ES/COS/Nacos。

## 其他说明

- 接口文档：`http://localhost:8003/api/doc.html`
- 配置文件：后端环境隔离使用 `application.yml`（默认 `dev`）、`application-test.yml`、`application-prod.yml`
- 部署或代理需求可调整前端 `config/proxy.ts` 与 `src/constants/index.ts` 中的后端地址。
