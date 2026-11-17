package com.si1v3r.si1v3rApi.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.si1v3r.si1v3rApi.constant.UserConstant;
import com.si1v3r.si1v3rApi.controller.UserController;
import com.si1v3r.si1v3rApi.controller.WxMpController;
import com.si1v3r.si1v3rApi.annotation.AuthCheck;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.mapper.UserMapper;
import com.si1v3r.si1v3rApi.model.dto.user.*;
import com.si1v3r.si1v3rApi.model.dto.post.PostQueryRequest;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.InterfaceInfoQueryRequest;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.vo.AdminUserVO;
import com.si1v3r.si1v3rApi.service.UserService;
import com.si1v3r.si1v3rApi.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityGuardTest {
    private static final String PASSWORD = "test-password-123";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private UserMapper mapper;
    private UserServiceImpl service;

    @BeforeEach
    void setup() {
        mapper = mock(UserMapper.class);
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private User user(String password, String role) {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("tester");
        user.setUserPassword(password);
        user.setUserRole(role);
        return user;
    }

    @Test
    void registrationStoresBCryptAndCanLoginWithRotatedSession() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            ((User) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        assertEquals(1L, service.userRegister("tester", PASSWORD, PASSWORD));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(mapper).insert(saved.capture());
        User user = saved.getValue();
        assertTrue(user.getUserPassword().startsWith("$2a$"));
        assertTrue(encoder.matches(PASSWORD, user.getUserPassword()));
        when(mapper.selectOne(any())).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        String oldSessionId = request.getSession().getId();
        assertEquals(1L, service.userLogin("tester", PASSWORD, request).getId());
        assertNotEquals(oldSessionId, request.getSession().getId());
        assertSame(user, request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void legacyLoginUpgradesStoredPasswordBeforeEstablishingSession() {
        User user = user(DigestUtils.md5DigestAsHex((UserServiceImpl.SALT + PASSWORD)
                .getBytes(StandardCharsets.UTF_8)), "user");
        when(mapper.selectOne(any())).thenReturn(user);
        when(mapper.updateById(any())).thenReturn(1);
        service.userLogin("tester", PASSWORD, new MockHttpServletRequest());
        ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
        verify(mapper).updateById(updated.capture());
        assertEquals(1L, updated.getValue().getId());
        assertTrue(encoder.matches(PASSWORD, updated.getValue().getUserPassword()));
        assertEquals(user.getUserPassword(), updated.getValue().getUserPassword());
    }

    @Test
    void wrongPasswordsAndBannedUsersNeverEstablishSession() {
        User user = user(encoder.encode(PASSWORD), "user");
        when(mapper.selectOne(any())).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> service.userLogin("tester", "wrong-password", request));
        user.setUserRole("ban");
        assertEquals("账号已封禁", assertThrows(BusinessException.class,
                () -> service.userLogin("tester", PASSWORD, request)).getMessage());
        assertNull(request.getSession(false));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void bannedDatabaseUserCannotReuseAnExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user(null, "user"));
        when(mapper.selectById(1L)).thenReturn(user(null, "ban"));
        assertThrows(BusinessException.class, () -> service.getLoginUser(request));
        assertNull(service.getLoginUserPermitNull(request));
    }

    @Test
    void allThreeQueryWrappersIgnorePayloadsInvalidOrdersAndNullOrders() {
        for (String field : Arrays.asList("CASE WHEN 1=1 THEN id END", "userPassword", "secretKey", "id desc", null)) {
            assertQueries(field, "ascend", false);
        }
        for (String order : Arrays.asList(null, "", "desc", " descend", "ascend;DROP TABLE user")) {
            assertQueries("id", order, false);
        }
        assertQueries("id", "ascend", true);
        assertQueries("createTime", "descend", true);
        assertQueries("updateTime", "descend", true);
        PostQueryRequest post = new PostQueryRequest();
        post.setSortField("thumbNum");
        post.setSortOrder("descend");
        assertTrue(new PostServiceImpl().getQueryWrapper(post).getSqlSegment().contains("thumbNum DESC"));
        post.setSortField("favourNum");
        assertTrue(new PostServiceImpl().getQueryWrapper(post).getSqlSegment().contains("favourNum DESC"));
        assertQueries("thumbNum", "ascend", false, true);
    }

    private void assertQueries(String field, String order, boolean sorted) {
        assertQueries(field, order, sorted, false);
    }

    private void assertQueries(String field, String order, boolean sorted, boolean skipPost) {
        UserQueryRequest user = new UserQueryRequest();
        user.setSortField(field); user.setSortOrder(order);
        InterfaceInfoQueryRequest api = new InterfaceInfoQueryRequest();
        api.setSortField(field); api.setSortOrder(order);
        PostQueryRequest post = new PostQueryRequest();
        post.setSortField(field); post.setSortOrder(order);
        assertEquals(sorted, service.getQueryWrapper(user).getSqlSegment().contains("ORDER BY"));
        assertEquals(sorted, new InterfaceInfoServiceImpl().getQueryWrapper(api).getSqlSegment().contains("ORDER BY"));
        if (!skipPost) {
            assertEquals(sorted, new PostServiceImpl().getQueryWrapper(post).getSqlSegment().contains("ORDER BY"));
        }
    }

    @Test
    void adminViewsContainEveryNonSecretEntityFieldAndNeverSerializeCredentials() throws Exception {
        Set<String> expected = Arrays.stream(User.class.getDeclaredFields()).map(Field::getName)
                .filter(name -> !Arrays.asList("userPassword", "secretKey").contains(name)).collect(Collectors.toSet());
        assertEquals(expected, Arrays.stream(AdminUserVO.class.getDeclaredFields()).map(Field::getName)
                .collect(Collectors.toSet()));
        UserService users = mock(UserService.class);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", users);
        User user = user("sensitive-password-hash", "admin");
        user.setSecretKey("sensitive-secret"); user.setAssessKey("allowed-access-key");
        when(users.getById(1L)).thenReturn(user);
        when(users.getQueryWrapper(any())).thenReturn(new QueryWrapper<>());
        Page<User> page = new Page<>(1, 10, 1);
        page.setRecords(Arrays.asList(user));
        when(users.page(any(Page.class), any())).thenReturn(page);
        ObjectMapper json = new ObjectMapper();
        for (Object response : Arrays.asList(controller.getUserById(1L, new MockHttpServletRequest()),
                controller.listUserByPage(new UserQueryRequest(), new MockHttpServletRequest()))) {
            String serialized = json.writeValueAsString(response);
            assertFalse(serialized.contains("userPassword"));
            assertFalse(serialized.contains("secretKey"));
            assertFalse(serialized.contains("sensitive-"));
            assertTrue(serialized.contains("allowed-access-key"));
        }
    }

    @Test
    void adminAddRequiresExplicitPasswordAndHashesIt() {
        UserController controller = new UserController();
        UserService users = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "userService", users);
        UserAddRequest add = new UserAddRequest();
        assertThrows(BusinessException.class, () -> controller.addUser(add, new MockHttpServletRequest()));
        add.setUserPassword("short");
        assertThrows(BusinessException.class, () -> controller.addUser(add, new MockHttpServletRequest()));
        add.setUserPassword(PASSWORD);
        when(users.save(any())).thenReturn(true);
        controller.addUser(add, new MockHttpServletRequest());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertTrue(encoder.matches(PASSWORD, saved.getValue().getUserPassword()));
    }

    @Test
    void corsAllowsOnlyConfiguredExactOriginsWithCredentials() {
        com.si1v3r.si1v3rApi.config.CorsConfig config = new com.si1v3r.si1v3rApi.config.CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:8000, https://trusted.example");
        class Registry extends org.springframework.web.servlet.config.annotation.CorsRegistry {
            org.springframework.web.cors.CorsConfiguration cors() {
                return getCorsConfigurations().get("/**");
            }
        }
        Registry registry = new Registry();
        config.addCorsMappings(registry);
        assertEquals("http://localhost:8000", registry.cors().checkOrigin("http://localhost:8000"));
        assertEquals("https://trusted.example", registry.cors().checkOrigin("https://trusted.example"));
        assertNull(registry.cors().checkOrigin("https://untrusted.example"));
        assertNull(registry.cors().checkOrigin("http://localhost:8000.evil.example"));
        assertEquals(Boolean.TRUE, registry.cors().getAllowCredentials());
    }

    @Test
    void publicUserListChecksLoginAndSetMenuIsAdminPost() throws Exception {
        UserController controller = new UserController();
        UserService users = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "userService", users);
        when(users.getLoginUser(any())).thenThrow(new BusinessException(com.si1v3r.si1v3rApi.common.ErrorCode.NOT_LOGIN_ERROR));
        assertThrows(BusinessException.class, () -> controller.listUserVOByPage(new UserQueryRequest(), new MockHttpServletRequest()));
        verify(users, never()).page(any(), any());
        assertArrayEquals(new String[]{"/setMenu"}, WxMpController.class.getMethod("setMenu").getAnnotation(PostMapping.class).value());
        assertEquals(UserConstant.ADMIN_ROLE, WxMpController.class.getMethod("setMenu").getAnnotation(AuthCheck.class).mustRole());
    }
}
