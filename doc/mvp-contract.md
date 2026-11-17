# si1v3rApi MVP 开发约定（v1.3）

本文是 MVP 第一批任务（1、2、3、9、10、12）的**唯一共享约定**。所有 worker 必须遵守；如果发现约定有问题或无法实现，**先用 `ask` 向协调者提出，不要自行改约定**。

- 产品定位：**管理员发布平台自有 API**，用户/开发者拿 accessKey/secretKey 调用，网关统一鉴权、按次数计费。不是用户上传 API 的市场。
- 约定所在分支：`ZTHLLY/orchestrator`（协调者维护）。每个任务开始前，先在自己的分支上 `git merge ZTHLLY/orchestrator`，拿到本约定和之前已放行的所有代码。

---

## 0. 通用规则

### 0.1 任务顺序与分支

| 顺序 | 任务 | 分支 / worktree | 依赖 |
|---|---|---|---|
| 1 | T1 数据库建表 | `ZTHLLY/feat-database` | 无 |
| 2 | T2 公共模块 + Dubbo 服务；T3 原子扣减 | `ZTHLLY/feat-dubbo-invokeCount` | T1 |
| 3 | T9 网关鉴权 | `ZTHLLY/feat-apiGateway-role-check` | T2 |
| 4 | T10 网关接口校验 + 计次 | `ZTHLLY/feat-apiGateway-invokeCount` | T9 |
| 5 | T12 SDK 通用调用 | `ZTHLLY/feat-sdk` | T9 |

每个任务放行后，协调者把该分支合入 `ZTHLLY/orchestrator`，下一个任务再从 `ZTHLLY/orchestrator` 同步。

### 0.2 范围纪律
- 只改本任务列出的内容。发现范围外的问题，写进汇报的"遗留问题"，不要顺手改。
- 不改本约定文件（`doc/mvp-contract.md`）。不要往 `doc/` 或仓库其他位置提交验收报告、日志等一次性文件；验收证据写在 worker_done 汇报里或仓库外的临时目录。
- 不 push、不提 PR、不合并其他分支（除了开始时同步 `ZTHLLY/orchestrator`）。commit 由实现者在自己的分支上做，push 和 PR 由协调者负责。
- 不往仓库里提交任何真实密钥、密码。示例值用明显的占位符（如 `<your-access-key>`）。

### 0.3 构建环境（必须遵守）
- **所有 Maven 命令都必须用 JDK 17**：先执行
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
  系统默认的 JDK 25 编译不了本项目（Spring Boot 2.7 + Lombok 不支持），不要试图通过升级 Spring Boot/Lombok 来适配 JDK 25。
- 本机没有 `mvn`，用 `si1v3rApi-backend/mvnw`：`cd si1v3rApi-backend && ./mvnw -B -f <module>/pom.xml ...`
- SDK 的 `maven-compiler-plugin` 配的 `source/target=7`，JDK 17 不支持，改成 `1.8`（由 **T1** 顺手修，这是后续所有任务能编译的前提）。
- 协调者已验证：JDK 17 + SDK 改为 1.8 后，SDK、backend、gateway、virtualApi 都能编译通过。
- 构建顺序：SDK `install` → common `install`（T2 之后）→ backend / gateway / virtualApi `compile`/`test`。

### 0.3.1 集成测试用的 Docker 容器
允许用 Docker 临时启动 MySQL 8 和 Nacos 做集成测试，规则：
- 容器名必须带任务前缀，如 `si1v3r-t1-mysql`、`si1v3r-t2-nacos`；宿主机端口用不冲突的端口（如 MySQL `13306`、Nacos `18848`），通过启动参数或测试 profile 覆盖连接地址，**不要为此修改仓库里的默认配置**。
- 只操作自己创建的容器，测试结束后 `docker rm -f` 删除；不要动其他已有容器、镜像、volume。
- 汇报里写明用了哪些容器和命令，以及是否已经删除。

### 0.4 每个任务的完成标准（Definition of Done）
1. 本任务的验收标准全部满足。
2. 涉及的模块能编译（按 0.3 的方式）；新增逻辑有单元测试并通过。确实无法运行的验证（例如本机没有 MySQL/Nacos），在汇报里写明"未运行 + 原因"，不能写成通过。
3. 在自己分支上 commit，提交信息用英文、写清楚做了什么。
4. 用 `worker_done` 汇报：改了哪些文件、怎么验证的（命令 + 结果）、遗留问题、对约定的任何偏离。

### 0.5 命名
- 现有代码里 accessKey 拼成了 `assessKey`（实体字段、请求头、配置项都是）。**MVP 阶段保持 `assessKey` 不改名**，避免牵连前端和老代码；改名留到以后统一做。

---

## 1. 数据库（T1）

数据库名统一为 **`si1v3rApi`**（与 `application.yml` 一致）。

### 1.1 `user` 表新增列
| 列 | 类型 | 约束 |
|---|---|---|
| `assessKey` | `varchar(256)` | `null`，唯一索引 `uk_assessKey` |
| `secretKey` | `varchar(512)` | `null` |

### 1.2 `interface_info` 表（与实体 `InterfaceInfo` 字段一一对应）
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint auto_increment` 主键 | |
| `name` | `varchar(256) not null` | |
| `description` | `varchar(256) null` | |
| `url` | `varchar(512) not null` | **网关路径**，如 `/api/user`，不含协议和主机 |
| `requestParams` | `text null` | |
| `requestHeader` | `text null` | |
| `responseHeader` | `text null` | |
| `status` | `int default 0 not null` | 0-关闭，1-开启 |
| `method` | `varchar(256) not null` | 大写，如 `GET`、`POST` |
| `userId` | `bigint not null` | 创建人（管理员） |
| `createTime` / `updateTime` | `datetime` | 同 user 表写法 |
| `isDelete` | `tinyint default 0 not null` | 逻辑删除 |

索引：`idx_url_method (url, method)`。

### 1.3 `user_interface_info` 表（与实体 `UserInterfaceInfo` 对应）
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `bigint auto_increment` 主键 | |
| `userId` | `bigint not null` | |
| `interfaceInfoId` | `bigint not null` | |
| `totalNum` | `int default 0 not null` | 累计已调用次数（**MVP 唯一使用的计数字段**） |
| `leftNum` | `int default 0 not null` | **v1.2 起废弃**：平台不做限额，代码不读不写，列暂时保留，后续统一清理 |
| `status` | `int default 0 not null` | **v1.2 起废弃**：同上 |
| `createTime` / `updateTime` / `isDelete` | | 同上 |

唯一索引：`uk_user_interface (userId, interfaceInfoId)`。

### 1.4 脚本文件
- `si1v3rApi-backend/sql/create_table.sql`：全新建库用，包含上面所有表（保留原有 post 等模板表）。
- `si1v3rApi-backend/sql/migrate_mvp_v1.sql`：已有旧库升级用（只做 `ALTER`/`CREATE TABLE IF NOT EXISTS`，可重复执行）。
- `si1v3rApi-backend/sql/seed_dev.sql`：开发用种子数据，只插入 virtualApi 的接口记录（`/api/user POST`、`/api/name POST`、`/api/ GET`，status=1），**不插入任何用户和密钥**。

---

## 2. 公共模块与 Dubbo 服务（T2、T3）

### 2.1 模块
- 路径：`si1v3rApi-backend/si1v3rApi-common`
- 坐标：`com.si1v3r:si1v3rApi-common:0.0.1-SNAPSHOT`，Java 1.8
- 根包：`com.si1v3r.si1v3rapicommon`
- 依赖尽量少：只需要 Lombok（可选）和 `java.io.Serializable`。**不能依赖 MyBatis、Spring Web。**
- backend 和 gateway 都依赖它；backend 和 gateway 里的 `DemoService` 及网关启动时的 `sayHello` 调用一并删除（由 T2 完成）。

### 2.2 RPC 数据对象（包 `com.si1v3r.si1v3rapicommon.model`，都实现 `Serializable`）

```java
public class InvokeUser {          // 只给网关鉴权用，不含密码
    Long id;
    String assessKey;
    String secretKey;
    String userRole;               // user / admin / ban
}

public class InvokeInterface {
    Long id;
    String url;                    // 网关路径，如 /api/user
    String method;                 // 大写
    Integer status;                // 0-关闭，1-开启
}
```

### 2.3 RPC 接口（包 `com.si1v3r.si1v3rapicommon.service`）

```java
public interface InnerUserService {
    /** 按 assessKey 查用户；不存在或已逻辑删除返回 null。是否 ban 由网关判断。 */
    InvokeUser getInvokeUser(String assessKey);
}

public interface InnerInterfaceInfoService {
    /** 按网关路径 + 方法（大写）精确匹配未删除的接口；不存在返回 null。是否上线由网关判断。 */
    InvokeInterface getInterfaceInfo(String url, String method);
}

public interface InnerUserInterfaceInfoService {
    /**
     * v1.2：调用成功后记一次调用（只统计，不限额）。必须是一条原子 SQL：
     * INSERT INTO user_interface_info (userId, interfaceInfoId, totalNum) VALUES (?, ?, 1)
     * ON DUPLICATE KEY UPDATE totalNum = totalNum + 1, isDelete = 0
     * 依赖唯一索引 uk_user_interface；第一次调用自动建记录。执行成功返回 true（参数非法返回 false）。
     */
    boolean invokeCount(long interfaceInfoId, long userId);
}
```

- backend 用 `@DubboService` 实现这三个接口，放在 `com.si1v3r.si1v3rApi.service.impl.inner` 包。
- 现有 `UserInterfaceInfoServiceImpl.invokeCount` 改为上面的原子实现，`InnerUserInterfaceInfoService` 的实现直接复用它。
- **v1.2 产品决定：平台只统计调用次数，不做限额。** `hasLeftNum` 删除；任何通过鉴权的用户都可以调用已上线的接口。`leftNum`、`status` 两列不再读写。
- 并发时计数准确（原子 `+1`，不丢计数）。

### 2.4 Dubbo 配置
- backend：`dubbo.application.name=si1v3rApi-backend`，协议端口保持 `22221`。
- gateway：`dubbo.application.name=si1v3rApi-gateway`，`qos-enable: false`。
- 注册中心保持 `nacos://localhost:8848`（凭据外置是后续任务，不在本批次）。
- 网关侧 `@DubboReference(check = false)`，避免 backend 没启动时网关起不来。

---

## 3. 签名协议（T9 实现校验，T12 实现生成）

### 3.1 请求头
| 头 | 内容 |
|---|---|
| `assessKey` | 用户的 accessKey |
| `timestamp` | Unix 秒 |
| `nonce` | 32 位小写十六进制随机串（`SecureRandom` 生成 16 字节） |
| `sign` | 签名，见下 |

旧的 `body` 请求头**废弃**，不再发送也不再读取。

### 3.2 签名算法
```
bodyDigest = lowercaseHex(SHA-256(请求体原始字节))      // 没有请求体时，对空字节数组求 SHA-256
canonical  = METHOD + "\n"
           + path + "\n"                                // 如 /api/user，不含 query
           + rawQuery + "\n"                            // URL 里 ? 后面的原始字符串，原样使用；没有就是空串
           + bodyDigest + "\n"
           + assessKey + "\n"
           + timestamp + "\n"
           + nonce
sign       = lowercaseHex(HMAC-SHA256(key = secretKey 的 UTF-8 字节, data = canonical 的 UTF-8 字节))
```

- 实现放在 SDK 的 `com.si1v3r.si1v3rapiclientsdk.utils.SignUtils`，新增方法 `public static String sign(String method, String path, String rawQuery, byte[] body, String assessKey, String timestamp, String nonce, String secretKey)`。**由 T9 实现**（网关已依赖 SDK），T12 直接调用，不得另写一份。
- 旧的 `SignUtils.getSign(Map, String)` 删除。

**固定测试向量**（协调者已用 openssl 独立验证；SDK 和网关的测试都必须包含它）：

| 输入 | 值 |
|---|---|
| method / path / rawQuery | `POST` / `/api/user` / `name=Alice%20Smith&x=1` |
| body（UTF-8） | `{"name":"Alice"}` |
| bodyDigest | `3cba1e3cf23c8ce24b7e08171d823fbd9a4929aafd9f27516e30699d3a42026a` |
| assessKey / timestamp / nonce | `test-access` / `1700000000` / `0123456789abcdef0123456789abcdef` |
| secretKey | `test-secret` |
| **sign** | `8789b341bc02a61bcc5830fe2faec700a368159ad1dd96b20b4850a251895a3b` |

### 3.3 网关校验规则（v1.1 修订）
校验顺序：**path 规范性 → 头格式 → 时间戳 → 请求体大小（Content-Length）→ 查用户 → 读请求体（带上限）→ 验签 → 判 ban → 登记 nonce**。先查用户再读请求体，让未知 key 不消耗内存。

- **path 规范性**：`getURI().getRawPath()` 含以下任一内容即拒绝（大小写不敏感）→ 400：`//`、`/./`、`/../`、以 `/.` 或 `/..` 结尾、`;`、`\`、`%2e`、`%2f`、`%5c`、`%00`。
- 4 个头任一缺失或格式不对 → 401。`timestamp` 必须是 1–12 位、无前导零的十进制数字；`nonce` 必须是 32 位小写十六进制。
- `|now - timestamp| > 300` 秒 → 401。
- **请求体上限**：可配置 `si1v3r.gateway.max-body-bytes`，默认 `1048576`（1 MiB）。`Content-Length` 超限直接拒绝；读取时也要用带上限的读取（如 `DataBufferUtils.join(body, max)`），chunked 超限同样拒绝 → 413。
- `getInvokeUser` 返回 null → 401；签名错误 → 401；签名正确但 `userRole = ban` → 403。
- 签名比较用常量时间比较（`MessageDigest.isEqual`）。
- **nonce 防重放**：
  - 只有签名正确的请求才登记 nonce，登记必须原子（同一 nonce 并发只有一个成功）。
  - nonce 按 `(assessKey, nonce)` 记录，保留到 **`max(now, timestamp) + 301` 秒**，保证在时间戳彻底失效之前 nonce 一直有效（防止边界重放和未来时间戳重放）。
  - **每个 assessKey 的配额**：同时保留的 nonce 数不超过 `si1v3r.gateway.nonce-per-key-max`（默认 `10000`），超出只拒绝该 key → 429。
  - **全局兜底**：总数不超过 `si1v3r.gateway.nonce-global-max`（默认 `500000`），超出 → 503（fail closed，不驱逐未过期的 nonce）。
  - 过期清理不能在每个请求里全表扫描（用按过期时间排序的结构或定时清理）。
  - MVP 用网关进程内缓存；多实例部署需要换 Redis（后续任务）。
- **转发前移除鉴权头**：鉴权通过后，转发给下游的请求里删除 `assessKey`、`timestamp`、`nonce`、`sign` 四个头。
- RPC 调用之后，后续链路要回到原来的调度线程（例如 `publishOn` 回事件循环），不要让下游过滤器在 boundedElastic 线程上执行。

### 3.4 网关错误响应
HTTP 状态码 + JSON 体 `{"code": <int>, "message": "<string>"}`，`Content-Type: application/json`。

| 场景 | HTTP | code |
|---|---|---|
| path 不规范 | 400 | 40000 |
| 缺头 / 签名错 / 时间戳过期 / nonce 重复 / 用户不存在 | 401 | 40100 |
| 用户被封禁 | 403 | 40300 |
| 请求体超过上限 | 413 | 41300 |
| 该 assessKey 的 nonce 配额已满 | 429 | 42900 |
| nonce 全局容量已满 | 503 | 50300 |
| 接口不存在 | 404 | 40400 |
| 接口未上线 | 403 | 40301 |
| 网关内部错误（如 RPC 失败） | 500 | 50000 |

`message` 用简短英文，不暴露内部细节。

---

## 4. 网关过滤器拆分（T9、T10）

| 过滤器 | 负责任务 | `getOrder()` | 职责 |
|---|---|---|---|
| `AuthGlobalFilter` | T9 | `-100` | 读取并缓存请求体、校验签名/时间戳/nonce、查用户、判 ban；通过后把 `InvokeUser` 放进 `exchange.getAttributes()`，key 为常量 `GatewayAttrs.INVOKE_USER = "si1v3r.invokeUser"` |
| `InterfaceInvokeGlobalFilter` | T10 | `-50` | 查接口、判上线；转发；响应状态为 2xx 时调用 `invokeCount` 记一次调用；记录响应日志 |

- **T10 查接口必须用 `request.getURI().getRawPath()` 原样作为 `url` 参数**（AuthGlobalFilter 已保证它是规范的），与签名用的 path 完全一致；method 用 `request.getMethodValue()`。不得自行解码或规范化后再查。
- 两个过滤器放在 `com.si1v3r.si1v3rapigateway.filter` 包；常量类 `GatewayAttrs` 和错误响应工具 `GatewayErrors` 放 `com.si1v3r.si1v3rapigateway.support`，由 **T9 创建**，T10 只复用。
- T9 删除旧的 `CustomGlobalFilter`；它里面的响应日志装饰逻辑由 T10 在新过滤器里重写。T9 完成后、T10 之前，网关应能"鉴权通过即直接转发"。
- 请求体在 T9 读取后必须重新包装进 exchange，保证下游还能拿到完整请求体。
- 删除网关路由里的演示过滤器 `AddRequestHeader=si1v3r, shuai`、`AddRequestParameter=name, blue`、`RewritePath=...`（T9）。路由保持一条：`/api/** → http://localhost:8123`。
- 删除 IP 白名单（来源控制交给部署层，后续任务处理）（T9）。
- 日志：不打印请求体、响应体、签名、secretKey；只记录 requestId、method、path、assessKey、状态码、耗时。
- `invokeCount` 返回 false 或 RPC 失败时，只记 warn 日志，不改变已经返回给客户端的响应。

---

## 5. virtualApi（T9 顺带）
- 删除 `NameController` 里自己那套硬编码 key 校验（签名协议变了，它会把所有经网关的请求拒掉）。接口路径不变：`GET /api/`、`POST /api/name`、`POST /api/user`。
- 删除 `virtualApi/src/main/resources/application.yml` 里的 `si1v3r.client.assess-key/secret-key`。
- "只允许经网关访问"不在本批次。

---

## 6. SDK（T12）
- 网关地址可配置：配置项 `si1v3r.client.gateway-host`，默认 `http://localhost:8090`；构造器同时提供 `(assessKey, secretKey)` 和 `(assessKey, secretKey, gatewayHost)`。
- 新增通用方法：
  ```java
  public ApiResponse invoke(String method, String path, Map<String, String> query, String jsonBody)
  ```
  `ApiResponse` 包含 `int status` 和 `String body`。SDK 自己拼出 query 字符串，并用**同一个字符串**参与签名。
- 所有请求都按第 3 节签名；nonce 用 `SecureRandom`。
- 保留 `getUsernameByPost(User)`、`getNameByGet`、`getNameByPost` 方法签名（backend 还在用），内部改为调用 `invoke`。
- 删除所有 `System.out.println`。
- 去掉原 `setKeys` / 私有 `getSign`。

---

## 7. 本批次明确不做（后续任务）
在线调试按接口 url 调用、用户密钥查看/重置、普通用户接口列表、前端改动、Redis、凭据外置、按接口动态路由、virtualApi 网络隔离、密码改 BCrypt、SQL 注入等安全修复。

**已知过渡期影响**：T9 之后，backend 里用配置文件 `si1v3r.client` 固定 key（`admin01`）的"发布前校验"和"在线调试"会因为数据库里没有这个 key 而失败。这两个功能本来就有问题，会在后续任务（在线调试 / 密钥管理）里重做，本批次不修。

---
---

# 第二批（v1.3）

第二批 5 个任务。第 0 节通用规则（JDK 17、Docker 规则、范围纪律、完成标准、不提交验收报告）**继续适用**；下面只写增量。

## B0. 第二批通用规则

### B0.1 任务、分支与顺序
| 波次 | 任务 | 分支 / worktree | 主要改动范围 |
|---|---|---|---|
| 1（并行） | S1 安全加固 | `ZTHLLY/feat-security-guard` | 用户登录注册/密码、admin 用户接口返回值、排序字段、日志、CORS/Cookie、`WxMpController` |
| 1（并行） | S2 接口列表与接口管理 | `ZTHLLY/feat-user-api-list` | `InterfaceController`（除 `/invoke`）、接口相关 DTO/Service |
| 1（并行） | S3 清理无用代码 + 我的调用统计 | `ZTHLLY/chro-clear-unused` | `user_interface_info` 全模块、SQL、重复前端目录、virtualApi 测试 |
| 2 | S4 用户密钥 + 在线调试 | `ZTHLLY/feat-user-key` | 注册时密钥生成、密钥接口、`InterfaceController#/invoke`、backend 的 `si1v3r.client` 配置 |
| 3 | S5 前端 | `ZTHLLY/feat-frontend-design` | `si1v3rApi-frontend/src`、`config/routes.ts` |

- 开始时：`git merge ZTHLLY/orchestrator`（包含第一批全部代码 + 本约定）。
- **文件归属**：同一波并行的任务只改自己范围内的文件。必须改别人范围的文件时，先 `ask` 协调者。波次之间靠合并 orchestrator 传递代码。
- 每个任务放行后，协调者把分支合入 `ZTHLLY/orchestrator`；下一波开始时再同步。

### B0.2 PR 方式（第二批起）
- 每个任务的 PR 目标分支是 **`ZTHLLY/orchestrator`**（集成分支），放行后由协调者合并。
- 第二批全部完成后，协调者提一个 `ZTHLLY/orchestrator → main` 的总 PR，交给用户合并。

### B0.3 审查标准（MVP 级别）
- 只有 **blocker** 才打回：主流程跑不通、可被直接利用的安全漏洞、数据丢失或错乱、编译/测试失败、违反约定导致其他任务接不上。
- 其他 major / minor / nit 记录到 PR 的"已知限制"，不打回、不追加修补。

### B0.4 通用接口约定（backend）
- 所有新接口返回现有的 `BaseResponse<T>`（`ResultUtils.success(...)` / `BusinessException`），前缀 `/api`（context-path）。
- "需要登录"用 `userService.getLoginUser(request)`；"仅管理员"用 `@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)`。
- 分页请求沿用 `PageRequest`（`current`、`pageSize`、`sortField`、`sortOrder`）。

---

## S1. 安全加固（feat-security-guard）

1. **排序字段白名单**：`SqlUtils` 改为白名单方式，例如 `SqlUtils.validSortField(String sortField, Set<String> allowed)`。不在白名单里的排序字段直接忽略（不排序），不报错。`sortOrder` 只接受 `ascend`/`descend`，`null` 不能报 NPE。在 User、Post、InterfaceInfo 三处 `getQueryWrapper` 中使用，白名单只放安全的列：`id`、`createTime`、`updateTime`，Post 另加 `thumbNum`、`favourNum`。**不要改 `UserInterfaceInfo` 相关代码**（归 S3）。
2. **敏感字段不出接口**：
   - `/user/get`、`/user/list/page`（管理员）改为返回 `AdminUserVO`：包含 `User` 除 `userPassword`、`secretKey` 以外的所有字段，`assessKey` 可以保留。
   - `/user/list/page/vo` 改为需要登录。
3. **密码改 BCrypt**：
   - 加 `spring-security-crypto` 依赖（只加这一个，不引入 Spring Security 框架）。
   - 注册和管理员新增用户时，用 BCrypt 存密码。
   - 登录时：存储值是 BCrypt 的，用 BCrypt 校验；存储值是旧的 32 位 MD5 的，按旧算法 `md5(SALT + pw)` 校验，**校验通过后立刻改存 BCrypt**（透明升级）。
   - `/user/add` 不再使用固定默认密码。`UserAddRequest` 增加 `userPassword` 字段，必填，至少 8 位。
4. **封禁生效**：`userRole=ban` 的用户密码登录失败（报"账号已封禁"）；`getLoginUser` 遇到 ban 用户也视为未登录。
5. **会话**：登录成功后 `request.changeSessionId()`；`application.yml` 配置 session cookie 为 `http-only: true`、`same-site: lax`。
6. **CORS**：只允许配置项 `si1v3r.cors.allowed-origins` 中的来源（逗号分隔，默认 `http://localhost:8000`），继续允许携带凭证。
7. **日志**：
   - `LogInterceptor` 不再打印请求参数，只记录 requestId、path、耗时。
   - MyBatis 的 `log-impl: StdOutImpl` 从默认配置中删除。
8. **`/setMenu`**：改为 POST，并且仅管理员可用。

## S2. 接口列表与接口管理（feat-user-api-list）

1. **普通用户接口列表** `POST /interfaceInfo/list/page/vo`：
   - 需要登录。
   - 只返回 `status=1`（已上线）且未删除的接口。
   - `pageSize` 最大 20，超过报参数错误。
   - 支持按 `name`、`description` 模糊搜索。
   - 返回 `Page<InterfaceInfoVO>`。
   - **不要改 `InterfaceInfoServiceImpl.getQueryWrapper`**（排序白名单归 S1）。上线过滤可以在 controller 里给 wrapper 追加条件，或者新加一个 service 方法。
2. **接口管理只限管理员**：
   - `/interfaceInfo/add`、`/interfaceInfo/delete` 加 `@AuthCheck(admin)`。
   - `delete` 去掉"本人或管理员"的判断。
   - `InterfaceInfoAddRequest` 删除 `id` 字段。
3. **新增和修改接口时的校验**：
   - `url` 必须以 `/api/` 开头，并且是规范路径，规则同约定 3.3 的 path 规范性检查。
   - `method` 统一转成大写，只允许 `GET`、`POST`、`PUT`、`DELETE`。
   - `name` 不能为空。
4. **上线 `/online`**：删除"上线前通过网关调一次 `/api/user`"的校验。网关会拒绝未上线的接口，这个校验做不到；改为只检查接口是否存在、`url`/`method` 是否合法，然后设为上线。删除 `InterfaceController` 里不再使用的 `si1v3rApiClient` 注入。**不要改 `/invoke` 方法**（归 S4）。
5. `InterfaceInfoVO` 对普通用户只暴露这些字段：`id`、`name`、`description`、`url`、`method`、`requestParams`、`requestHeader`、`responseHeader`、`status`、`createTime`。不包含 `userId`。

## S3. 清理无用代码 + 我的调用统计（chro-clear-unused）

1. **彻底删除 `user_interface_info` 中的 `leftNum`、`status`**：
   - 改动范围：实体、DTO（Add/Update/Query）、`validUserInterfaceInfo`、mapper XML、`create_table.sql`。
   - 新增 `sql/migrate_mvp_v2.sql`：可以重复执行，删除这两列（先检查列是否存在）。
   - `UserInterfaceInfoController` 中只保留管理员的 `get`、`delete`，以及下面第 2 项。`add`、`update` 删除：计数只能由网关写入，不允许手动修改。
2. **我的调用统计** `GET /userInterfaceInfo/my/list`：
   - 需要登录。
   - 返回当前用户的 `List<MyInvokeStatVO>`，字段为 `interfaceInfoId`、`interfaceName`、`url`、`method`、`totalNum`、`updateTime`（最后一次调用时间）。
   - 只包含未删除的记录，按 `totalNum` 降序排列。
   - 接口已被删除的记录也要返回，`interfaceName` 返回 `null`。
3. 删除重复的前端目录 `si1v3rApi-frontend/si1v3rApi-frontend/`。
4. `virtualApi` 的 `VirtualApiApplicationTests.clintTest` 依赖真实运行的网关，删掉这个测试方法，保留 `contextLoads`。
5. **不要动** `UserServiceImpl`、`UserController`、`InterfaceController`、`SqlUtils`、`application.yml`。

## S4. 用户密钥 + 在线调试（feat-user-key，第 2 波）

1. **生成密钥**：
   - 注册时用 `SecureRandom` 生成。`assessKey` 为 16 字节，转 32 位小写十六进制；`secretKey` 为 32 字节，转 64 位小写十六进制。
   - `assessKey` 和库里已有的值冲突时重新生成。
   - 删除旧的 MD5 生成逻辑，以及那段写死 key 的死代码。
2. **接口**：
   - `GET /user/key`：需要登录，返回 `UserKeyVO {assessKey, secretKey}`。当前用户还没有密钥时，先生成再返回。
   - `POST /user/key/regenerate`：需要登录，重新生成密钥并返回 `UserKeyVO`。旧 key 立即失效，因为网关每次请求都会查库。
3. **在线调试 `POST /interfaceInfo/invoke`**（请求为 `{id, requestParams}`）：
   - 需要登录；接口必须存在且已上线。
   - 用当前用户自己的密钥，以及配置项 `si1v3r.client.gateway-host`（默认 `http://localhost:8090`），构造 `new Si1v3rApiClient(ak, sk, gatewayHost)`，按接口记录里的 `method`、`url` 调用 `invoke`：
     - `GET` / `DELETE`：`requestParams` 按扁平 JSON 对象解析成 query。为空时不带 query；不是对象时报参数错误。
     - `POST` / `PUT`：`requestParams` 原样作为 JSON body。为空时发送 `{}`。
   - 返回 `InvokeResultVO {status, body}`，即网关的原始状态码和响应体。网关返回非 2xx 时也正常返回这个 VO，不抛业务异常。
4. **配置清理**：
   - 删除 backend `application.yml` 里的 `si1v3r.client.assess-key`、`secret-key`，保留并新增 `si1v3r.client.gateway-host`。
   - 删除 `UserInterfaceInfoController` 等处未使用的 `Si1v3rApiClient` 注入。

## S5. 前端（feat-frontend-design，第 3 波）

后端接口以 S1–S4 合并后的代码为准。可以启动 backend 后执行 `openapi` 重新生成 `src/services`，也可以手写，二选一。

1. **路由**：修复 `/` 重复定义的问题。最终路由如下：
   - 首页 `/` → 接口列表
   - `/interface/:id` → 接口详情 + 在线调试
   - `/user/login`、`/user/register`
   - `/account/center` → 个人中心
   - `/admin/user`、`/admin/api`（仅管理员）
2. **首页**：调用 `/interfaceInfo/list/page/vo`，支持搜索和分页，点击进入详情页。
3. **接口详情**：
   - 展示接口信息。
   - 在线调试：输入 `requestParams`，调用 `/interfaceInfo/invoke`，展示返回的 `status` 和 `body`。非 2xx 用醒目颜色显示。
4. **个人中心**：
   - 我的密钥：展示 `assessKey`；`secretKey` 默认打码，可以切换显示、可以复制；"重新生成"按钮需要二次确认。
   - 我的调用统计：表格展示 `/userInterfaceInfo/my/list` 的数据。
5. **管理员接口管理**：新增和修改表单增加 `url`、`method` 的前端校验，规则与 S2 一致。
6. **杂项**：`BACKEND_HOST_PROD` 不能再是 `https://yupi.icu`，改为占位符并加注释说明需要替换；删除前端里对已经不存在的后端接口的调用。
7. **验收**：`pnpm install` 后，`pnpm run tsc` 和 `pnpm build` 都通过；启动完整后端链路，用浏览器自动化走通主流程（注册 → 登录 → 首页 → 详情页调试成功 → 个人中心能看到密钥和调用次数 +1），并截图作为证据。
