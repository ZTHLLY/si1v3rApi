package com.si1v3r.si1v3rApi.controller;

import com.si1v3r.si1v3rApi.exception.BusinessException;
import com.si1v3r.si1v3rApi.model.dto.interfaceinfo.InterfaceInfoInvokeRequest;
import com.si1v3r.si1v3rApi.model.entity.InterfaceInfo;
import com.si1v3r.si1v3rApi.model.entity.User;
import com.si1v3r.si1v3rApi.model.vo.InvokeResultVO;
import com.si1v3r.si1v3rApi.model.vo.UserKeyVO;
import com.si1v3r.si1v3rApi.service.InterfaceInfoService;
import com.si1v3r.si1v3rApi.service.UserService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InterfaceInvokeS4Test {
    private HttpServer server;
    private InterfaceController controller;
    private InterfaceInfo info;
    private UserService users;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> assessKey = new AtomicReference<>();
    private int status = 200;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assessKey.set(exchange.getRequestHeaders().getFirst("assessKey"));
            byte[] response = "gateway-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        controller = new InterfaceController();
        users = mock(UserService.class);
        InterfaceInfoService interfaces = mock(InterfaceInfoService.class);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "interfaceInfoService", interfaces);
        ReflectionTestUtils.setField(controller, "gatewayHost", "http://127.0.0.1:" + server.getAddress().getPort());
        User user = new User(); user.setId(7L);
        when(users.getLoginUser(any())).thenReturn(user);
        UserKeyVO keys = new UserKeyVO(); keys.setAssessKey("current-key"); keys.setSecretKey("current-secret");
        when(users.getUserKey(7L, false)).thenReturn(keys);
        info = new InterfaceInfo(); info.setId(1L); info.setStatus(1); info.setUrl("/api/user");
        when(interfaces.getById(1L)).thenReturn(info);
    }

    @AfterEach
    void cleanup() { server.stop(0); }

    private InvokeResultVO invoke(String verb, String params) {
        info.setMethod(verb);
        InterfaceInfoInvokeRequest input = new InterfaceInfoInvokeRequest();
        input.setId(1L); input.setRequestParams(params);
        return controller.invokeInterfaceInfo(input, new MockHttpServletRequest()).getData();
    }

    @Test
    void getAndDeleteSendEncodedFlatQueryWithoutBody() {
        for (String verb : new String[]{"GET", "DELETE"}) {
            assertEquals(200, invoke(verb, "{\"name\":\"x y\",\"n\":2}").getStatus());
            assertEquals(verb, method.get());
            assertEquals("n=2&name=x%20y", query.get());
            assertEquals("", body.get());
            assertEquals("current-key", assessKey.get());
        }
        invoke("GET", " ");
        assertNull(query.get());
    }

    @Test
    void postAndPutPreserveBodyAndReturnNonSuccessGatewayResponse() {
        for (String verb : new String[]{"POST", "PUT"}) {
            String json = "{ \"name\": \"S4\" }";
            InvokeResultVO result = invoke(verb, json);
            assertEquals(200, result.getStatus());
            assertEquals("gateway-body", result.getBody());
            assertEquals(json, body.get());
            assertNull(query.get());
            invoke(verb, null);
            assertEquals("{}", body.get());
        }
        status = 401;
        assertEquals(401, invoke("POST", "{}").getStatus());
    }

    @Test
    void invalidParamsReturnParameterErrorWithoutInvokingGateway() {
        for (String params : new String[]{"[]", "null", "1", "{bad}", "{} {}", "{\"nested\":{}}", "{\"array\":[]}"}) {
            BusinessException error = assertThrows(BusinessException.class, () -> invoke("GET", params));
            assertEquals(40000, error.getCode());
        }
        assertThrows(BusinessException.class, () -> invoke("POST", "{bad}"));
        verify(users, never()).getUserKey(anyLong(), anyBoolean());
        assertNull(method.get());
    }

    @Test
    void offlineInterfaceIsRejected() {
        info.setStatus(0);
        assertThrows(BusinessException.class, () -> invoke("POST", "{}"));
        verify(users, never()).getUserKey(anyLong(), anyBoolean());
    }
}
