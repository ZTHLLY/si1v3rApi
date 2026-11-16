package com.si1v3r.si1v3rApi.service.inner;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import com.si1v3r.si1v3rApi.service.impl.*;
import com.si1v3r.si1v3rApi.service.impl.inner.*;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rapicommon.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL integration tests; initialize the isolated database with sql/create_table.sql first. */
@SpringBootTest(classes = InnerServicesIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("t2-integration")
@EnabledIfEnvironmentVariable(named = "T2_MYSQL_URL", matches = ".+")
class InnerServicesIntegrationTest {
    @Configuration
    @MapperScan("com.si1v3r.si1v3rApi.mapper")
    @Import({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class, UserServiceImpl.class, InterfaceInfoServiceImpl.class,
            UserInterfaceInfoServiceImpl.class, InnerUserServiceImpl.class,
            InnerInterfaceInfoServiceImpl.class, InnerUserInterfaceInfoServiceImpl.class})
    static class Config { }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserInterfaceInfoService counts;
    @Autowired private InnerUserInterfaceInfoService innerCounts;
    @Autowired private InnerUserService users;
    @Autowired private InnerInterfaceInfoService interfaces;

    @BeforeEach
    @AfterEach
    void cleanFixtures() {
        // Only remove IDs reserved for this test class, including logically deleted rows.
        jdbc.update("DELETE FROM user_interface_info WHERE userId IN (1900000101,-1900000101,0) "
                + "AND interfaceInfoId IN (1900000201,-1900000201,0)");
        jdbc.update("DELETE FROM interface_info WHERE id IN (1900000201,1900000202)");
        jdbc.update("DELETE FROM user WHERE id=1900000101");
    }

    private void assertTotalAndSingleRow(int total) {
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) AS rowCount, SUM(totalNum) AS total "
                + "FROM user_interface_info WHERE userId=1900000101 AND interfaceInfoId=1900000201");
        assertEquals(1, ((Number) row.get("rowCount")).intValue());
        assertEquals(total, ((Number) row.get("total")).intValue());
    }

    @Test
    void firstCallAutomaticallyCreatesRecord() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_interface_info "
                + "WHERE userId=1900000101 AND interfaceInfoId=1900000201", Integer.class));
        assertTrue(innerCounts.invokeCount(1900000201, 1900000101));
        assertTotalAndSingleRow(1);
    }

    @Test
    void subsequentCallsIncrementExistingRecord() {
        assertTrue(innerCounts.invokeCount(1900000201, 1900000101));
        assertTrue(counts.invokeCount(1900000201, 1900000101));
        assertTrue(innerCounts.invokeCount(1900000201, 1900000101));
        assertTotalAndSingleRow(3);
    }

    @Test
    void deletedRecordIsRestoredAndIncremented() {
        jdbc.update("INSERT INTO user_interface_info(userId,interfaceInfoId,totalNum,isDelete) "
                + "VALUES (1900000101,1900000201,7,1)");
        assertTrue(innerCounts.invokeCount(1900000201, 1900000101));
        assertTotalAndSingleRow(8);
        assertEquals(0, jdbc.queryForObject("SELECT isDelete FROM user_interface_info "
                + "WHERE userId=1900000101 AND interfaceInfoId=1900000201", Integer.class));
    }

    @Test
    void invalidParametersDoNotCreateRecords() {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM user_interface_info", Integer.class);
        for (long[] pair : new long[][] {{0,1900000101}, {-1900000201,1900000101},
                {1900000201,0}, {1900000201,-1900000101}, {0,0}, {-1900000201,-1900000101}}) {
            assertFalse(counts.invokeCount(pair[0], pair[1]));
            assertFalse(innerCounts.invokeCount(pair[0], pair[1]));
        }
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM user_interface_info", Integer.class));
    }

    @Test
    void twentyConcurrentFirstCallsCreateOneRowAndCountEveryCall() throws Exception {
        // No pre-created relation: the unique pair index must arbitrate concurrent inserts.
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new TimeoutException(); }
                    return innerCounts.invokeCount(1900000201, 1900000101);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Boolean> result : results) assertTrue(result.get(20, TimeUnit.SECONDS));
            assertTotalAndSingleRow(20);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void usersAreMinimalSerializableDtosAndDeletedUsersAreHidden() {
        assertNull(users.getInvokeUser("missing"));
        assertNull(users.getInvokeUser(null));
        assertNull(users.getInvokeUser(""));
        jdbc.update("INSERT INTO user(id,userAccount,userPassword,assessKey,secretKey,userRole) "
                + "VALUES (1900000101,'test-account','<test-password>','<test-access-key>','<test-secret-key>','ban')");
        InvokeUser user = users.getInvokeUser("<test-access-key>");
        assertNotNull(user);
        assertEquals(InvokeUser.class, user.getClass());
        assertTrue(user instanceof Serializable);
        assertEquals(Long.valueOf(1900000101), user.getId());
        assertEquals("<test-access-key>", user.getAssessKey());
        assertEquals("<test-secret-key>", user.getSecretKey());
        assertEquals("ban", user.getUserRole());
        Set<String> fields = Arrays.stream(InvokeUser.class.getDeclaredFields())
                .map(Field::getName).filter(name -> !name.equals("serialVersionUID")).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList("id", "assessKey", "secretKey", "userRole")), fields);
        jdbc.update("UPDATE user SET isDelete=1 WHERE id=1900000101");
        assertNull(users.getInvokeUser("<test-access-key>"));
    }

    @Test
    void interfaceLookupMatchesExactPathAndNormalizesMethod() {
        jdbc.update("INSERT INTO interface_info(id,name,url,method,status,userId) "
                + "VALUES (1900000201,'test','/api/user','POST',0,1900000101)");
        assertEquals(Long.valueOf(1900000201), interfaces.getInterfaceInfo("/api/user", "POST").getId());
        assertEquals("POST", interfaces.getInterfaceInfo("/api/user", "post").getMethod());
        assertEquals(Integer.valueOf(0), interfaces.getInterfaceInfo("/api/user", "post").getStatus());
        assertNull(interfaces.getInterfaceInfo("/api/user", "GET"));
        assertNull(interfaces.getInterfaceInfo("/api/User", "POST"));
        assertNull(interfaces.getInterfaceInfo("/api/user/", "POST"));
        assertNull(interfaces.getInterfaceInfo("/api/use", "POST"));
        assertNull(interfaces.getInterfaceInfo(null, "POST"));
        assertNull(interfaces.getInterfaceInfo("", "POST"));
        assertNull(interfaces.getInterfaceInfo("/api/user", null));
        jdbc.update("UPDATE interface_info SET isDelete=1 WHERE id=1900000201");
        assertNull(interfaces.getInterfaceInfo("/api/user", "POST"));
    }

    @Test
    void duplicateInterfacesReturnSmallestMatchingId() {
        // Insert the larger ID first to ensure the choice does not depend on insertion order.
        jdbc.update("INSERT INTO interface_info(id,name,url,method,status,userId) "
                + "VALUES (1900000202,'duplicate','/api/user','POST',1,1900000101),"
                + "(1900000201,'first','/api/user','POST',0,1900000101)");
        assertEquals(Long.valueOf(1900000201), assertDoesNotThrow(
                () -> interfaces.getInterfaceInfo("/api/user", "post")).getId());
    }

    @Test
    void userKeysRequireExactCaseAndNoTrailingSpace() {
        jdbc.update("INSERT INTO user(id,userAccount,userPassword,assessKey,secretKey) "
                + "VALUES (1900000101,'t2-exact-key','<test-password>','RevKey','<test-secret-key>')");
        assertNotNull(users.getInvokeUser("RevKey"));
        assertNull(users.getInvokeUser("revkey"));
        assertNull(users.getInvokeUser("RevKey "));
    }
}
