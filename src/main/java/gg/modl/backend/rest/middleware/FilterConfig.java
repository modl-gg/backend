package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import gg.modl.backend.settings.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {
    private final ServerService serverService;
    private final ApiKeyService apiKeyService;

    @Value("${modl.development-mode:false}")
    private boolean developmentMode;

    @Value("${modl.dev.server-domain:}")
    private String devServerDomain;

    @Bean
    public FilterRegistrationBean<ServerHeaderFilter> serverDomainFilter() {
        FilterRegistrationBean<ServerHeaderFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ServerHeaderFilter(serverService, developmentMode, devServerDomain));
        registrationBean.addUrlPatterns(RESTMappingV1.PREFIX_PANEL + "/*", RESTMappingV1.PREFIX_PUBLIC + "/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> minecraftApiKeyFilter() {
        FilterRegistrationBean<ApiKeyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ApiKeyFilter(apiKeyService));
        registrationBean.addUrlPatterns(RESTMappingV1.PREFIX_MINECRAFT + "/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }
}
