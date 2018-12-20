package org.venice.piazza.piazza;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.idam.authn.PiazzaAuthenticator;

@Configuration
@Profile({ "disable-authn" })
public class DisableAuthnConfiguration {
	@Bean
	public PiazzaAuthenticator piazzaAuthenticator() {
		return null;
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
