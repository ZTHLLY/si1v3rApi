package com.si1v3r.si1v3rApi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.si1v3r.si1v3rApi.aop.AuthInterceptor;
import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.*;
import com.si1v3r.si1v3rApi.model.entity.InterfaceInfo;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.vo.InterfaceInfoVO;
import com.si1v3r.si1v3rApi.common.IdRequest;
import com.si1v3r.si1v3rApi.service.InterfaceInfoService;
import com.si1v3r.si1v3rApi.service.UserService;
import com.si1v3r.si1v3rApi.service.impl.InterfaceInfoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterfaceControllerS2Test {
    private InterfaceController controller;
    private InterfaceInfoService service;
    private UserService users;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final InterfaceInfoServiceImpl validation = new InterfaceInfoServiceImpl();

    @BeforeEach
    void setup() {
        controller = new InterfaceController();
        service = mock(InterfaceInfoService.class);
        users = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "interfaceInfoService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        User user = new User(); user.setId(1L); user.setUserRole("user");
        when(users.getLoginUser(any())).thenReturn(user);
    }

    private InterfaceInfo valid() {
        InterfaceInfo info = new InterfaceInfo();
        info.setId(7L); info.setName("Example"); info.setUrl("/api/example"); info.setMethod("post");
        return info;
    }

    @Test
    void listRequiresLoginAndLimitsPageSize() {
        InterfaceInfoQueryRequest query = new InterfaceInfoQueryRequest();
        query.setPageSize(21);
        assertThrows(BusinessException.class, () -> controller.listInterfaceInfoVOByPage(query, request));
        verifyNoInteractions(service);
        query.setPageSize(20);
        when(users.getLoginUser(request)).thenThrow(new BusinessException(40100, "not logged in"));
        assertThrows(BusinessException.class, () -> controller.listInterfaceInfoVOByPage(query, request));
        verifyNoInteractions(service);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listFiltersOnlineAndUndeletedAndSupportsSearch() {
        InterfaceInfoQueryRequest query = new InterfaceInfoQueryRequest();
        query.setName("Example"); query.setDescription("details"); query.setPageSize(20);
        when(service.getQueryWrapper(query)).thenAnswer(i -> validation.getQueryWrapper(query));
        when(service.page(any(Page.class), any(QueryWrapper.class))).thenAnswer(i -> {
            QueryWrapper<InterfaceInfo> wrapper = i.getArgument(1);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("status =")); assertTrue(sql.contains("isDelete ="));
            assertTrue(sql.contains("name LIKE")); assertTrue(sql.contains("description LIKE"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(1));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(0));
            Page<InterfaceInfo> page = i.getArgument(0);
            page.setTotal(1); page.setRecords(Collections.singletonList(valid())); return page;
        });
        Page<InterfaceInfoVO> result = controller.listInterfaceInfoVOByPage(query, request).getData();
        assertEquals(1, result.getTotal()); assertEquals("Example", result.getRecords().get(0).getName());
        verify(users).getLoginUser(request);
    }

    @Test
    void addAndDeleteRejectOrdinaryUsersThroughRealAuthAspect() {
        AuthInterceptor auth = new AuthInterceptor(); ReflectionTestUtils.setField(auth, "userService", users);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller); factory.addAspect(auth);
        InterfaceController proxy = factory.getProxy();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThrows(BusinessException.class, () -> proxy.addInterfaceInfo(new InterfaceInfoAddRequest(), request));
            assertThrows(BusinessException.class, () -> proxy.deleteInterfaceInfo(new com.si1v3r.si1v3rApi.common.DeleteRequest(), request));
            verifyNoInteractions(service);
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }

    @Test
    void rejectsNonCanonicalPathsOnAddAndUpdate() {
        for (String path : Arrays.asList("http://host/api/test", "/other", "/api/../test", "/api/./test",
                "/api//test", "/api/test/..", "/api/test/.", "/api/a;b", "/api/a\\b", "/api/%2Etest",
                "/api/%2f", "/api/%5C", "/api/%00", "/api/test?q=1", "/api/test#fragment")) {
            InterfaceInfo info = valid(); info.setUrl(path);
            assertThrows(BusinessException.class, () -> validation.validInterfaceInfo(info, true), path);
            assertThrows(BusinessException.class, () -> validation.validInterfaceInfo(info, false), path);
        }
    }

    @Test
    void normalizesMethodsAndRejectsBlankNameOrUnsupportedMethod() {
        for (String method : Arrays.asList("get", "post", "put", "delete")) {
            InterfaceInfo info = valid(); info.setMethod(method); validation.validInterfaceInfo(info, true);
            assertEquals(method.toUpperCase(java.util.Locale.ROOT), info.getMethod());
        }
        InterfaceInfo info = valid(); info.setMethod("PATCH");
        assertThrows(BusinessException.class, () -> validation.validInterfaceInfo(info, true));
        info.setMethod("POST"); info.setName("  ");
        assertThrows(BusinessException.class, () -> validation.validInterfaceInfo(info, false));
        info.setName(null);
        assertThrows(BusinessException.class, () -> validation.validInterfaceInfo(info, true));
    }

    @Test
    void onlineOnlyValidatesAndUpdatesWithoutGatewayClient() {
        InterfaceInfo info = valid(); when(service.getById(7L)).thenReturn(info);
        doAnswer(i -> { validation.validInterfaceInfo(i.getArgument(0), true); return null; })
                .when(service).validInterfaceInfo(any(), eq(true));
        when(service.updateById(any())).thenAnswer(i -> {
            InterfaceInfo update = i.getArgument(0); assertEquals(1, update.getStatus());
            assertEquals("POST", update.getMethod()); return true;
        });
        IdRequest id = new IdRequest(); id.setId(7L);
        assertTrue(controller.onlineInterfaceInfo(id).getData());
        verify(service).validInterfaceInfo(info, true);
        assertFalse(Arrays.stream(InterfaceController.class.getDeclaredFields())
                .anyMatch(f -> f.getType().getSimpleName().equals("Si1v3rApiClient")));
    }

    @Test
    void voAndAddDtoExposeOnlyAllowedFields() throws Exception {
        InterfaceInfo info = valid(); info.setUserId(99L);
        String json = new ObjectMapper().writeValueAsString(InterfaceInfoVO.objToVo(info));
        assertFalse(json.contains("userId")); assertFalse(json.contains("updateTime"));
        assertThrows(NoSuchFieldException.class, () -> InterfaceInfoAddRequest.class.getDeclaredField("id"));
    }
}
