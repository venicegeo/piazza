package org.venice.piazza.piazza;

import java.security.Principal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.annotations.Api;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class DocsConfiguration {

	@Bean
	public Docket gatewayApi() {
		return new Docket(DocumentationType.SWAGGER_2).useDefaultResponseMessages(false).ignoredParameterTypes(Principal.class)
				.groupName("Piazza").apiInfo(apiInfo()).select().apis(RequestHandlerSelectors.withClassAnnotation(Api.class))
				.paths(PathSelectors.any()).build();
	}

	private ApiInfo apiInfo() {
		return new ApiInfoBuilder().title("Gateway API").description("Piazza Core Services API")
				.contact(new Contact("The VeniceGeo Project", "http://radiantblue.com", "venice@radiantblue.com")).version("0.1.0").build();
	}

}
