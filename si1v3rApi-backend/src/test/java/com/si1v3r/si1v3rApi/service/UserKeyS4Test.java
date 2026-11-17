package com.si1v3r.si1v3rApi.service;

import com.si1v3r.si1v3rApi.controller.UserController;
import com.si1v3r.si1v3rApi.mapper.UserMapper;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.vo.UserKeyVO;
import com.si1v3r.si1v3rApi.service.impl.UserServiceImpl;
import com.si1v3r.si1v3rApi.service.impl.inner.InnerUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserKeyS4Test {
    private UserMapper mapper;
    private UserServiceImpl service;

    @BeforeEach
    void setup() {
        mapper = mock(UserMapper.class);
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectCount(any())).thenReturn(0L);
    }

    private void assertKeys(UserKeyVO keys) {
        assertTrue(keys.getAssessKey().matches("[0-9a-f]{32}"));
        assertTrue(keys.getSecretKey().matches("[0-9a-f]{64}"));
    }

    @Test
    void registrationUsesSecureHexKeysAndRetriesDatabaseCollision() {
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("collision"))
                .thenAnswer(call -> {
                    User user = call.getArgument(0);
                    assertTrue(user.getAssessKey().matches("[0-9a-f]{32}"));
                    assertTrue(user.getSecretKey().matches("[0-9a-f]{64}"));
                    user.setId(7L);
                    return 1;
                });
        assertEquals(7L, service.userRegister("test-s4", "password123", "password123"));
        verify(mapper, times(2)).insert(any());
    }

    @Test
    void existingAssessKeyCollisionIsRegeneratedBeforeSaving() {
        when(mapper.selectCount(any())).thenReturn(0L, 1L, 0L);
        when(mapper.insert(any())).thenAnswer(call -> { ((User) call.getArgument(0)).setId(7L); return 1; });
        assertEquals(7L, service.userRegister("test-s4", "password123", "password123"));
        verify(mapper, times(3)).selectCount(any());
        verify(mapper).insert(any());
    }

    @Test
    void oldUserKeyEndpointCreatesKeysOnceAndRegenerationInvalidatesOldKey() {
        User user = new User();
        user.setId(7L);
        when(mapper.selectById(7L)).thenReturn(user);
        when(mapper.updateById(any())).thenAnswer(call -> {
            User update = call.getArgument(0);
            user.setAssessKey(update.getAssessKey());
            user.setSecretKey(update.getSecretKey());
            return 1;
        });
        UserServiceImpl loginService = spy(service);
        doReturn(user).when(loginService).getLoginUser(any());
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", loginService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        UserKeyVO old = controller.getUserKey(request).getData();
        assertKeys(old);
        assertEquals(old, controller.getUserKey(request).getData());
        verify(mapper).updateById(any());
        UserKeyVO fresh = controller.regenerateUserKey(request).getData();
        assertKeys(fresh);
        assertNotEquals(old.getAssessKey(), fresh.getAssessKey());
        assertNotEquals(old.getSecretKey(), fresh.getSecretKey());
        when(mapper.selectOne(any())).thenReturn(user);
        InnerUserServiceImpl inner = new InnerUserServiceImpl();
        ReflectionTestUtils.setField(inner, "userService", service);
        assertNull(inner.getInvokeUser(old.getAssessKey()));
        assertNotNull(inner.getInvokeUser(fresh.getAssessKey()));
    }
}
