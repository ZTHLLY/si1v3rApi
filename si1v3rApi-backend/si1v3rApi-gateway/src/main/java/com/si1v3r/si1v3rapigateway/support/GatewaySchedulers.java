package com.si1v3r.si1v3rapigateway.support;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.atomic.AtomicReference;

public final class GatewaySchedulers {
    private GatewaySchedulers() { }

    public static Scheduler originalScheduler(ServerHttpRequest request) {
        Object nativeRequest;
        try {
            nativeRequest = ServerHttpRequestDecorator.getNativeRequest(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            nativeRequest = null;
        }
        if (nativeRequest instanceof reactor.netty.http.server.HttpServerRequest) {
            AtomicReference<Scheduler> scheduler = new AtomicReference<>();
            ((reactor.netty.http.server.HttpServerRequest) nativeRequest).withConnection(connection ->
                    scheduler.set(Schedulers.fromExecutor(connection.channel().eventLoop())));
            return scheduler.get();
        }
        // Mock/non-Netty exchanges have no transport event loop; never run their chain on the RPC pool.
        return Schedulers.parallel();
    }

}
