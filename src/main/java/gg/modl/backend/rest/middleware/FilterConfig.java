package gg.modl.backend.rest.middleware;

import gg.modl.backend.cors.DynamicCorsConfigurationSource;
import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {
    private final ServerService serverService;
    private final DynamicCorsConfigurationSource corsConfigurationSource;
    private final ApiKeyFilter apiKeyFilter;

    @Value("${modl.development-mode:false}")
    private boolean developmentMode;

    @Value("${modl.dev.server-domain:}")
    private String devServerDomain;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        FilterRegistrationBean<CorsFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CorsFilter((CorsConfigurationSource) corsConfigurationSource));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ServerHeaderFilter> serverDomainFilter() {
        FilterRegistrationBean<ServerHeaderFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ServerHeaderFilter(serverService, developmentMode, devServerDomain));
        registrationBean.addUrlPatterns(RESTMappingV1.PREFIX_PANEL + "/*", RESTMappingV1.PREFIX_PUBLIC + "/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyFilter> registrationBean = new FilterRegistrationBean<>(apiKeyFilter);
        registrationBean.setEnabled(false);
        return registrationBean;
    }
}
