# si1v3rApi MVP 开发约定（v1）

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
- 不改本约定文件（`doc/mvp-contract.md`）。
- 不 push、不提 PR、不合并其他分支（除了开始时同步 `ZTHLLY/orchestrator`）。commit 由实现者在自己的分支上做，push 和 PR 由协调者负责。
- 不往仓库里提交任何真实密钥、密码。示例值用明显的占位符（如 `<your-access-key>`）。

### 0.3 构建环境（已知问题，必须处理）
本机只有 JDK 25；没有 `mvn`，用 `si1v3rApi-backend/mvnw`（`./mvnw -f <module>/pom.xml ...`）。

- SDK 的 `maven-compiler-plugin` 配的 `source/target=7`，JDK 25 不支持，需要改成 `1.8`（由 **T1** 顺手修，这是让后续所有任务能编译的前提）。
- Lombok（Boot 2.7 默认 1.18.24）在 JDK 25 上注解处理失效，编译报 `cannot find symbol`（`log`、getter 等）。协调者已验证 `-Dlombok.version=1.18.42 -Dmaven.compiler.proc=full` **仍不够**。这个问题的处理方式见协调者在任务说明里的指示；在拿到指示前，不要自行升级 Spring Boot 或改大版本。
- 构建顺序：SDK `install` → common `install`（T2 之后）→ backend / gateway / virtualApi `compile`/`test`。

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
| `totalNum` | `int default 0 not null` | 累计已调用次数 |
| `leftNum` | `int default 0 not null` | 剩余次数 |
| `status` | `int default 0 not null` | 0-正常，1-禁用 |
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
    /** 该用户对该接口是否还能调用：存在关系记录、status=0、leftNum>0。 */
    boolean hasLeftNum(long interfaceInfoId, long userId);

    /**
     * 调用成功后扣减一次。必须是一条原子 SQL：
     * UPDATE user_interface_info SET leftNum = leftNum - 1, totalNum = totalNum + 1
     * WHERE interfaceInfoId = ? AND userId = ? AND status = 0 AND leftNum > 0 AND isDelete = 0
     * 影响行数为 1 返回 true，否则 false。
     */
    boolean invokeCount(long interfaceInfoId, long userId);
}
```

- backend 用 `@DubboService` 实现这三个接口，放在 `com.si1v3r.si1v3rApi.service.impl.inner` 包。
- 现有 `UserInterfaceInfoServiceImpl.invokeCount` 改为上面的原子实现（T3），`InnerUserInterfaceInfoService` 的实现直接复用它。
- 已知限制（MVP 接受）：先查 `hasLeftNum` 再转发、成功后才扣减，并发时可能有少量调用没被扣到；但 `leftNum` 永远不会变成负数。

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

### 3.3 网关校验规则
- 4 个头任一缺失或格式不对 → 401。
- `|now - timestamp| > 300` 秒 → 401。
- nonce 在 300 秒内重复 → 401。MVP 用网关进程内带过期的缓存；多实例部署需要换 Redis（后续任务）。
- `getInvokeUser` 返回 null，或 `userRole = ban` → 401 / 403（见 3.4）。
- 签名比较用常量时间比较（`MessageDigest.isEqual`）。

### 3.4 网关错误响应
HTTP 状态码 + JSON 体 `{"code": <int>, "message": "<string>"}`，`Content-Type: application/json`。

| 场景 | HTTP | code |
|---|---|---|
| 缺头 / 签名错 / 时间戳过期 / nonce 重复 / 用户不存在 | 401 | 40100 |
| 用户被封禁 | 403 | 40300 |
| 接口不存在 | 404 | 40400 |
| 接口未上线 | 403 | 40301 |
| 没有剩余次数或关系被禁用 | 403 | 40302 |
| 网关内部错误（如 RPC 失败） | 500 | 50000 |

`message` 用简短英文，不暴露内部细节。

---

## 4. 网关过滤器拆分（T9、T10）

| 过滤器 | 负责任务 | `getOrder()` | 职责 |
|---|---|---|---|
| `AuthGlobalFilter` | T9 | `-100` | 读取并缓存请求体、校验签名/时间戳/nonce、查用户、判 ban；通过后把 `InvokeUser` 放进 `exchange.getAttributes()`，key 为常量 `GatewayAttrs.INVOKE_USER = "si1v3r.invokeUser"` |
| `InterfaceInvokeGlobalFilter` | T10 | `-50` | 查接口、判上线、`hasLeftNum`；转发；响应状态为 2xx 时调用 `invokeCount`；记录响应日志 |

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
在线调试按接口 url 调用、用户密钥查看/重置、管理员分配次数、普通用户接口列表、前端改动、Redis、凭据外置、按接口动态路由、virtualApi 网络隔离、密码改 BCrypt、SQL 注入等安全修复。

**已知过渡期影响**：T9 之后，backend 里用配置文件 `si1v3r.client` 固定 key（`admin01`）的"发布前校验"和"在线调试"会因为数据库里没有这个 key 而失败。这两个功能本来就有问题，会在后续任务（在线调试 / 密钥管理）里重做，本批次不修。
