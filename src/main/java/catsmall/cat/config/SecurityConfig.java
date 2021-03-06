package catsmall.cat.config;

import catsmall.cat.admin.manage.handler.CustomLoginFailureHandler;
import catsmall.cat.admin.manage.handler.CustomLoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private final DataSource dataSource;
    private final CustomLoginSuccessHandler customLoginSuccessHandler;
    private final CustomLoginFailureHandler customLoginFailureHandler;

    public AccessDecisionManager accessDecisionManager(){
        RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
        hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER");

        DefaultWebSecurityExpressionHandler handler = new DefaultWebSecurityExpressionHandler();
        handler.setRoleHierarchy(hierarchy);

        WebExpressionVoter voter = new WebExpressionVoter();
        voter.setExpressionHandler(handler);

        List<AccessDecisionVoter<? extends Object>> voters = Arrays.asList(voter);
        return new AffirmativeBased(voters);
    }


    // Form?????? ?????? ?????? ?????????
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .mvcMatchers("/", "/api/**","/board", "/item/**", "/search/**", "/profile", "/application/health",
                        "/members/signin", "/members/signup", "/members/logout", "/members/forgot/**").permitAll()
                .mvcMatchers("/admin/**","/admin").hasRole("ADMIN")
                .anyRequest().authenticated();

        http
                .formLogin()
                .loginPage("/members/signin")
                .loginProcessingUrl("/members/signin")
                .successHandler(customLoginSuccessHandler)
                .failureHandler(customLoginFailureHandler);

        http
                .logout()
                .logoutSuccessUrl("/");

        http
                .rememberMe()
                .key("token")
                .tokenRepository(jdbcTokenRepository())
                .rememberMeParameter("duringLogin")
                .rememberMeCookieName("duringLogin")
                .tokenValiditySeconds(60 * 60 * 24 * 14); // ?????? 2?????? ??????


        http
                .httpBasic();

        http
                .sessionManagement()
                .sessionFixation()
                .changeSessionId()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .sessionRegistry(sessionRegistry()) // session clustering?????? ??????????????? ?????? ??? ???????????????????????????
                .expiredUrl("/members/signin");
    }
    // WAS?????? ??????????????? ?????? ???????????????
    @Bean
    public SessionRegistry sessionRegistry(){
        return new SessionRegistryImpl();
    }
    // SessionRegistry?????? ??????, ????????? ????????? WAS????????? ???????????????, spring security?????? ????????? ??????.
    // ?????? ????????? ???????????? ????????? ??????????????? ?????????????????????
    @Bean
    public static ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<HttpSessionEventPublisher>(new HttpSessionEventPublisher());
    }

    @Bean
    public PersistentTokenRepository jdbcTokenRepository(){
        JdbcTokenRepositoryImpl jdb = new JdbcTokenRepositoryImpl();
        jdb.setDataSource(dataSource);
        return jdb;
    }


    // static ????????? ?????? security ignore??????
    @Override
    public void configure(WebSecurity web) throws Exception {
        web
                .ignoring()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations())
                .mvcMatchers("/node_modules/**")
                .mvcMatchers("/upload/**");
    }
}
