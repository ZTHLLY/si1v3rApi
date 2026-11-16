package com.si1v3r.si1v3rapigateway.filter;

import com.si1v3r.si1v3rapicommon.model.InvokeInterface;
import com.si1v3r.si1v3rapicommon.model.InvokeUser;
import com.si1v3r.si1v3rapicommon.service.InnerInterfaceInfoService;
import com.si1v3r.si1v3rapicommon.service.InnerUserInterfaceInfoService;
import com.si1v3r.si1v3rapigateway.support.GatewayAttrs;
import com.si1v3r.si1v3rapigateway.support.GatewayErrors;
import com.si1v3r.si1v3rapigateway.support.GatewaySchedulers;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InterfaceInvokeGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceInvokeGlobalFilter.class);
    @DubboReference(check = false)
    private InnerInterfaceInfoService interfaceService;
    // Counting is non-idempotent: a timeout must never trigger a second deduction.
    @DubboReference(check = false, retries = 0)
    private InnerUserInterfaceInfoService userInterfaceService;

    public InterfaceInvokeGlobalFilter() { }
    InterfaceInvokeGlobalFilter(InnerInterfaceInfoService interfaces, InnerUserInterfaceInfoService users) {
        this.interfaceService = interfaces;
        this.userInterfaceService = users;
    }

    @Override
    public int getOrder() { return -50; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            InvokeUser user = exchange.getAttribute(GatewayAttrs.INVOKE_USER);
            String path = exchange.getRequest().getURI().getRawPath();
            String method = exchange.getRequest().getMethodValue();
            AtomicReference<Long> interfaceId = new AtomicReference<>();
            AtomicBoolean counted = new AtomicBoolean();
            AtomicBoolean accountingStarted = new AtomicBoolean();
            Runnable logResponse = () -> LOG.info(
                    "requestId={} method={} path={} assessKey={} interfaceId={} status={} elapsedMs={} counted={}",
                    exchange.getRequest().getId(), method, path,
                    user == null || user.getAssessKey() == null ? "" : user.getAssessKey().replaceAll("[\\p{Cntrl}]", ""),
                    interfaceId.get(), status(exchange), (System.nanoTime() - started) / 1000000, counted.get());
            Scheduler original = GatewaySchedulers.originalScheduler(exchange.getRequest());
            Mono<Void> result = user == null
                    ? GatewayErrors.write(exchange, HttpStatus.UNAUTHORIZED, 40100, "Unauthorized")
                    : Mono.fromCallable(() -> validate(path, method, user.getId(), interfaceId))
                        .subscribeOn(Schedulers.boundedElastic()).publishOn(original)
                        .flatMap(rejection -> {
                            if (rejection != 0) return reject(exchange, rejection);
                            // At order -50 the Netty response writer is inside this chain: count only
                            // after successful downstream completion, never on error or cancellation.
                            return chain.filter(exchange).then(Mono.defer(() -> {
                                if (status(exchange) < 200 || status(exchange) >= 300) return Mono.empty();
                                accountingStarted.set(true);
                                return Mono.fromCallable(() -> count(interfaceId.get(), user.getId()))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .doOnNext(counted::set).publishOn(original)
                                        .doFinally(signal -> logResponse.run())
                                        // A client may close immediately after receiving the response.
                                        // Do not interrupt/retry the non-idempotent accounting RPC.
                                        .cache().then();
                            }));
                        });
            return result.doFinally(signal -> {
                if (!accountingStarted.get()) logResponse.run();
            });
        });
    }

    private int validate(String path, String method, long userId, AtomicReference<Long> id) {
        try {
            InvokeInterface info = interfaceService.getInterfaceInfo(path, method);
            if (info == null) return 40400;
            id.set(info.getId());
            if (!Integer.valueOf(1).equals(info.getStatus())) return 40301;
            return userInterfaceService.hasLeftNum(info.getId(), userId) ? 0 : 40302;
        } catch (Exception e) { return 50000; }
    }

    private boolean count(long interfaceId, long userId) {
        boolean success;
        try { success = userInterfaceService.invokeCount(interfaceId, userId); }
        catch (Exception e) { success = false; }
        // Do not log RPC exception text: it may contain credentials or payloads.
        if (!success) LOG.warn("interfaceId={} counted=false", interfaceId);
        return success;
    }

    private static int status(ServerWebExchange exchange) {
        Integer status = exchange.getResponse().getRawStatusCode();
        return status == null ? 200 : status;
    }

    private static Mono<Void> reject(ServerWebExchange exchange, int code) {
        switch (code) {
            case 40400: return GatewayErrors.write(exchange, HttpStatus.NOT_FOUND, code, "Interface not found");
            case 40301: return GatewayErrors.write(exchange, HttpStatus.FORBIDDEN, code, "Interface offline");
            case 40302: return GatewayErrors.write(exchange, HttpStatus.FORBIDDEN, code, "No remaining calls");
            default: return GatewayErrors.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Internal server error");
        }
    }
}
