package org.venice.piazza.piazza;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.venice.piazza.gateway.auth.ExtendedRequestDetails;
import org.venice.piazza.gateway.auth.PiazzaBasicAuthenticationEntryPoint;
import org.venice.piazza.gateway.auth.PiazzaBasicAuthenticationProvider;

@Configuration
@Profile({ "secure" })
public class SecureConfiguration extends WebSecurityConfigurerAdapter {
	@Autowired
	private PiazzaBasicAuthenticationProvider basicAuthProvider;
	@Autowired
	private PiazzaBasicAuthenticationEntryPoint basicEntryPoint;

	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.authenticationProvider(basicAuthProvider);
	}

	@Override
	public void configure(WebSecurity web) throws Exception {
		web.ignoring().antMatchers("/key").antMatchers("/version").antMatchers("/").antMatchers(HttpMethod.OPTIONS)
				.antMatchers("/v2/key");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.httpBasic().authenticationEntryPoint(basicEntryPoint).authenticationDetailsSource(authenticationDetailsSource()).and()
				.authorizeRequests().anyRequest().authenticated().and().sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().csrf().disable();
	}

	/**
	 * Defines the Details that should be passed into the AuthenticationProvider details object.
	 */
	private AuthenticationDetailsSource<HttpServletRequest, ExtendedRequestDetails> authenticationDetailsSource() {
		return new AuthenticationDetailsSource<HttpServletRequest, ExtendedRequestDetails>() {
			@Override
			public ExtendedRequestDetails buildDetails(HttpServletRequest request) {
				return new ExtendedRequestDetails(request);
			}
		};
	}
}
