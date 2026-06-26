package gg.modl.backend.infrastructure.filter;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@Configuration
@Profile("staging")
public class StagingBetaAccessConfig {

    @Bean
    public FilterRegistrationBean<StagingBetaAccessFilter> stagingBetaAccessFilter() {
        final FilterRegistrationBean<StagingBetaAccessFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new StagingBetaAccessFilter());
        registrationBean.addUrlPatterns(RESTMappingV1.PREFIX_PANEL + "/*", RESTMappingV1.PREFIX_PUBLIC + "/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);

        return registrationBean;
    }
}
