package gg.modl.backend.rest.middleware;

import gg.modl.backend.rest.RESTMappingV1;
import gg.modl.backend.server.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {
    private final ServerService serverService;

    @Bean
    public FilterRegistrationBean<ServerHeaderFilter> panelFilter() {
        FilterRegistrationBean<ServerHeaderFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ServerHeaderFilter(serverService));
        registrationBean.addUrlPatterns(RESTMappingV1.PREFIX_PANEL + "/*", RESTMappingV1.PREFIX_PUBLIC + "/*");
        registrationBean.setOrder(1);

        return registrationBean;
    }
}
