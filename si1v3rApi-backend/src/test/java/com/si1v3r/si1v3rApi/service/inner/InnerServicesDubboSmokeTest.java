package com.si1v3r.si1v3rApi.service.inner;

import com.si1v3r.si1v3rapicommon.service.InnerInterfaceInfoService;
import com.si1v3r.si1v3rapicommon.service.InnerUserInterfaceInfoService;
import com.si1v3r.si1v3rapicommon.service.InnerUserService;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real RPC smoke test: run alone with -Dtest=InnerServicesDubboSmokeTest -Dt2.dubbo=true. */
@EnabledIfSystemProperty(named = "t2.dubbo", matches = "true")
class InnerServicesDubboSmokeTest {
    private final List<ReferenceConfig<?>> references = new ArrayList<>();

    private <T> T reference(Class<T> type, ApplicationConfig application) {
        ReferenceConfig<T> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setInterface(type);
        reference.setUrl(System.getProperty("t2.dubbo.url", "dubbo://127.0.0.1:22221") + "/" + type.getName());
        reference.setScope("remote");
        reference.setTimeout(5000);
        reference.setRetries(0);
        references.add(reference);
        return reference.get();
    }

    @Test
    void invokesAllThreeProvidersOverDubbo() {
        ApplicationConfig application = new ApplicationConfig("si1v3r-t2-smoke-consumer");
        application.setQosEnable(false);
        try {
            InnerUserService users = reference(InnerUserService.class, application);
            InnerInterfaceInfoService interfaces = reference(InnerInterfaceInfoService.class, application);
            InnerUserInterfaceInfoService counts = reference(InnerUserInterfaceInfoService.class, application);
            assertNull(users.getInvokeUser("<missing-smoke-access-key>"));
            assertNull(interfaces.getInterfaceInfo("/missing-t2-smoke", "post"));
            assertFalse(counts.hasLeftNum(Long.MAX_VALUE, Long.MAX_VALUE));
            assertFalse(counts.invokeCount(Long.MAX_VALUE, Long.MAX_VALUE));
        } finally {
            references.forEach(ReferenceConfig::destroy);
            DubboBootstrap.getInstance().destroy();
        }
    }
}
