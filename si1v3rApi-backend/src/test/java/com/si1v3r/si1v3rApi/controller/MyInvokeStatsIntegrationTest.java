package com.si1v3r.si1v3rApi.controller;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.si1v3r.si1v3rApi.constant.UserConstant;
import com.si1v3r.si1v3rApi.exception.GlobalExceptionHandler;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.service.UserInterfaceInfoService;
import com.si1v3r.si1v3rApi.service.impl.UserInterfaceInfoServiceImpl;
import com.si1v3r.si1v3rApi.service.impl.UserServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MyInvokeStatsIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("t2-integration")
@EnabledIfEnvironmentVariable(named = "T2_MYSQL_URL", matches = ".+")
class MyInvokeStatsIntegrationTest {
    @Configuration
    @MapperScan("com.si1v3r.si1v3rApi.mapper")
    @Import({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class, UserServiceImpl.class,
            UserInterfaceInfoServiceImpl.class, UserInterfaceInfoController.class})
    static class Config { }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserInterfaceInfoService counts;
    @Autowired private UserInterfaceInfoController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        clean();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        jdbc.update("INSERT INTO user(id,userAccount,userPassword) VALUES (1900000301,'s3-user','<test-password>')");
        jdbc.update("INSERT INTO interface_info(id,name,url,method,userId,isDelete) VALUES "
                + "(1900000401,'active','/api/user','POST',1900000301,0),"
                + "(1900000402,'deleted','/api/name','POST',1900000301,1)");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM user_interface_info WHERE userId IN (1900000301,1900000302)");
        jdbc.update("DELETE FROM interface_info WHERE id IN (1900000401,1900000402)");
        jdbc.update("DELETE FROM user WHERE id=1900000301");
    }

    private MockHttpSession session() {
        User user = new User();
        user.setId(1900000301L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return session;
    }

    @Test
    void listsOnlyOwnActiveCountsDescendingIncludingDeletedInterfaces() throws Exception {
        jdbc.update("INSERT INTO user_interface_info(userId,interfaceInfoId,totalNum,isDelete) VALUES "
                + "(1900000301,1900000401,2,0),(1900000301,1900000402,8,0),"
                + "(1900000301,1900000403,100,1),(1900000302,1900000401,500,0)");
        mvc.perform(get("/userInterfaceInfo/my/list").param("userId", "1900000302").session(session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].interfaceInfoId").value(1900000402L))
                .andExpect(jsonPath("$.data[0].totalNum").value(8))
                .andExpect(jsonPath("$.data[0].interfaceName").isEmpty())
                .andExpect(jsonPath("$.data[1].interfaceName").value("active"))
                .andExpect(jsonPath("$.data[1].url").value("/api/user"))
                .andExpect(jsonPath("$.data[1].method").value("POST"));
    }

    @Test
    void recordedCallAppearsInPersonalStats() throws Exception {
        counts.invokeCount(1900000401L, 1900000301L);
        mvc.perform(get("/userInterfaceInfo/my/list").session(session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].totalNum").value(1))
                .andExpect(jsonPath("$.data[0].updateTime").isNotEmpty());
    }

    @Test
    void requiresLoginAndEmptyHistoryIsAnEmptyList() throws Exception {
        mvc.perform(get("/userInterfaceInfo/my/list"))
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/userInterfaceInfo/my/list").session(session()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
