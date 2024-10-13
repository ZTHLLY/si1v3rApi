package com.si1v3r.si1v3rapigateway;


import com.si1v3r.si1v3rapiclientsdk.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> IP_WHITE_LIST= Arrays.asList("127.0.0.1");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1. 请求日志
        ServerHttpRequest request = exchange.getRequest();
        log.info("请求唯一标识："+request.getId());
        log.info("请求路径："+request.getPath().value());
        log.info("请求方法："+request.getMethod());
        log.info("请求参数："+request.getQueryParams());
        String sourceAddress = request.getLocalAddress().getHostString();
        log.info("请求来源地址："+sourceAddress);
        log.info("请求唯一标识："+request.getRemoteAddress());
        ServerHttpResponse response = exchange.getResponse();
        //2.黑白名单
        if(!IP_WHITE_LIST.contains(sourceAddress)){
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return response.setComplete();
        }
        //3.用户鉴权
        HttpHeaders headers2 = request.getHeaders();
        String assessKey = headers2.getFirst("assessKey");
        //String secretKey = request.getHeader("secretKey");
        String nonce = headers2.getFirst("nonce");
        String body = headers2.getFirst("body");
        String timestamp = headers2.getFirst("timestamp");
        String sign = headers2.getFirst("sign");

        Map<String,String> headers=new HashMap<>();
        headers.put("nonce",nonce);
        headers.put("body",body);
        headers.put("timestamp",timestamp);
        //这个secretKey正规流程要从数据库里查，我这里先直接用了
        //校验签名
        String secretKey="abcdefgh";
        String realSign = SignUtils.getSign(headers, secretKey);
        if(!sign.equals(realSign)){
            return handleNoau(response);
        }
        //校验assessKey
        if(!"admin01".equals(assessKey)){
            return handleNoau(response);
        }
        //校验签名
        if(!sign.equals(realSign)){
            return handleNoau(response);
        }
        //检验时间戳
        Long currentTime=System.currentTimeMillis()/1000;
        Long FIVE_MINUTES=60*5L;
        if(currentTime-Long.parseLong(timestamp)>=FIVE_MINUTES){
            return handleNoau(response);
        }
        //这里其实还要校验别的像随机数啊什么有的没的，我先不校验了
        //4. 判断请求接口是否存在，从数据库中查询模拟接口是否存在，以及请求方法是否匹配
        //5. 请求转发，调用模拟接口
        Mono<Void> filter=chain.filter(exchange);
        //6.响应日志
        //7.调用接口次数+1




        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> handleNoau(ServerHttpResponse response){
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }
}