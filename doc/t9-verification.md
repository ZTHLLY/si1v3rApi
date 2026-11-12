# T9 gateway authentication verification

Branch: `ZTHLLY/feat-apiGateway-role-check`.
Started by `git merge ZTHLLY/orchestrator` (fast-forward to `93dd6ab`) and read the complete `doc/mvp-contract.md`; the contract was not modified.

## Changes

- SDK `SignUtils`: replace the old map-based signer with the canonical HMAC-SHA256 signer; null body/query mean empty. `Si1v3rApiClient` is unchanged.
- Gateway: add `filter/AuthGlobalFilter`, `support/GatewayAttrs`, `support/GatewayErrors`; delete `CustomGlobalFilter`, its response decorator and IP whitelist. Preserve raw request bytes for downstream, perform blocking RPC on boundedElastic, compare signatures with `MessageDigest.isEqual`, publish `InvokeUser` after authentication.
- Nonces: atomically check/register per assessKey, only after valid signatures; 300-second lazy expiry, maximum 100,000 entries. A full cache fails closed with HTTP 500/code 50000 rather than evicting live nonces and enabling replay.
- Gateway route: only `/api/** -> http://localhost:8123`; delete demonstration filters; gateway logging is info, authentication logs contain only requestId/method/raw path/assessKey/result/elapsed time.
- virtualApi: remove controller-local hardcoded authentication and client-key configuration; preserve endpoint paths and response strings.
- Add SDK and gateway tests; explicitly configure Surefire 2.22.2 in both non-parented modules so JUnit 5 actually executes. The existing gateway context smoke test uses an isolated reactive context without Dubbo auto-configuration, while the real filter is tested with mocked RPC.

## Build and unit tests

Executed in this order from `si1v3rApi-backend`, all with:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./mvnw -B -f si1v3rApiClient-sdk/pom.xml install
./mvnw -B -f si1v3rApi-common/pom.xml install
./mvnw -B -f si1v3rApi-gateway/pom.xml test
./mvnw -B -f virtualApi/pom.xml compile
./mvnw -B compile
```

All five: **BUILD SUCCESS**. SDK: 2 tests, zero failures/errors; gateway: 15 tests (14 filter tests and 1 offline context smoke), zero failures/errors/skips. `git diff --check`: passed.

Filter coverage includes exact UTF-8 body forwarding and attributes, empty GET (including an empty body publisher), each missing header, malformed nonce/sign/timestamp and overflow timestamp, wrong signature, changed body/path/query, expired/future timestamps, replay, invalid signature not consuming the nonce, unknown user, ban, RPC failure, cache capacity/expiry, concurrent replay, and body-read failure. Every error assertion checks status, exact application/json Content-Type, code, message and the two-field JSON shape.

Final build logs: `/tmp/t9-sdk.log`, `/tmp/t9-common.log`, `/tmp/t9-gateway-final.log`, `/tmp/t9-virtual.log`, `/tmp/t9-backend.log`.

## Fixed signing vector (shared with T12)

All strings UTF-8, no final newline in canonical text:

| Input | Value |
|---|---|
| method | `POST` |
| path | `/api/user` |
| rawQuery | `name=Alice%20Smith&x=1` |
| body | `{"name":"Alice"}` |
| assessKey | `test-access` |
| timestamp | `1700000000` |
| nonce | `0123456789abcdef0123456789abcdef` |
| secretKey | `test-secret` (test fixture only) |
| SHA-256(body) | `3cba1e3cf23c8ce24b7e08171d823fbd9a4929aafd9f27516e30699d3a42026a` |
| expected sign | `8789b341bc02a61bcc5830fe2faec700a368159ad1dd96b20b4850a251895a3b` |

Independently computed with OpenSSL; the same commands are in `SignUtilsTest`:

```sh
printf '%s' '{"name":"Alice"}' | openssl dgst -sha256
printf 'POST\n/api/user\nname=Alice%%20Smith&x=1\n3cba1e3cf23c8ce24b7e08171d823fbd9a4929aafd9f27516e30699d3a42026a\ntest-access\n1700000000\n0123456789abcdef0123456789abcdef' | openssl dgst -sha256 -hmac 'test-secret'
```

## Real Docker / Dubbo / HTTP smoke

Created only these containers (test credentials only):

```sh
docker run -d --name si1v3r-t9-mysql -p 13306:3306 -e MYSQL_ROOT_PASSWORD=t9-test-password mysql:8
docker run -d --name si1v3r-t9-nacos -p 18848:8848 -p 19848:9848 -e MODE=standalone -e NACOS_AUTH_ENABLE=false nacos/nacos-server:v2.4.3
docker exec -i si1v3r-t9-mysql mysql -uroot -pt9-test-password < si1v3rApi-backend/sql/create_table.sql
docker exec si1v3r-t9-mysql mysql -uroot -pt9-test-password si1v3rApi -e "INSERT INTO user(userAccount,userPassword,assessKey,secretKey) VALUES ('t9-test','unused','t9-test-access','t9-test-secret');"
```

Generated classpaths with `./mvnw -B -f <module>/pom.xml dependency:build-classpath -Dmdep.outputFile=<file>`, then launched main classes with JDK 17 using `java -cp <target/classes>:<dependency-classpath> <main-class> <arguments>`.
Backend arguments override datasource URL to `jdbc:mysql://127.0.0.1:13306/si1v3rApi?useSSL=false&allowPublicKeyRetrieval=true`, password to the test fixture, HTTP port to 18003, registry to `nacos://127.0.0.1:18848`, and disable MyBatis SQL stdout logging for the smoke.
Gateway arguments override port to 8090 and registry to `nacos://127.0.0.1:18848`; virtualApi arguments override port to 8123. No repository connection settings were changed for the smoke.

Executed `python3 /tmp/t9-smoke.py` twice, including after the final implementation changes. It compiles `/tmp/T9Sign.java` against SDK target/classes and calls the actual `SignUtils.sign` with fresh Unix seconds and a UUID-hex nonce. Results from `POST http://localhost:8090/api/user` with raw body `{"name":"Alice"}`:

| Scenario | Result |
|---|---|
| Valid signature | 200, `Post hello Alice by post user` |
| No signature headers | 401, application/json, `{"code":40100,"message":"Unauthorized"}` |
| Replay identical signed request | 401, application/json, `{"code":40100,"message":"Unauthorized"}` |
| Set test userRole to ban, fresh signed request | 403, application/json, `{"code":40300,"message":"Forbidden"}` |

Final evidence: `/tmp/t9-smoke-final.log`; application logs: `/tmp/t9-.-app.log`, `/tmp/t9-virtualApi-app.log`, `/tmp/t9-si1v3rApi-gateway-app.log`.
The script's `finally` terminated and waited for every application process it launched; both runs confirmed cleanup. Then executed `docker rm -f si1v3r-t9-mysql si1v3r-t9-nacos`, deleting both containers; no other containers or volumes were touched.

## Review / remaining work

Separate manual review checked the protocol canonicalization, raw URI APIs, data-buffer release/replay, constant-time comparison, no downstream exception swallowing, nonce atomicity/capacity, and scope boundaries. Buffer handling was checked against the official [Spring 5.3 DataBufferUtils API](https://docs.spring.io/spring-framework/docs/5.3.23/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html).
No known T9 blockers or contract deviations. Interface validation/counting/response logging remain T10, SDK client migration remains T12, and the documented old-SDK/backend-debug transition is unchanged. No Redis, backend behavior changes, contract changes, push or PR.
