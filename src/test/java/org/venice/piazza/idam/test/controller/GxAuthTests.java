/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package org.venice.piazza.idam.test.controller;

import model.response.AuthResponse;
import model.security.authz.UserProfile;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.idam.authn.GxAuthenticator;
import org.venice.piazza.idam.authn.PiazzaAuthenticator;
import org.venice.piazza.idam.data.DatabaseAccessor;
import org.venice.piazza.idam.model.*;
import org.venice.piazza.idam.util.GxUserProfileClient;
import util.PiazzaLogger;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.when;

public class GxAuthTests {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private DatabaseAccessor accessor;
    @Mock
    private PiazzaLogger logger;
    @Mock
    private GxUserProfileClient userProfileClient;

    @InjectMocks
    private GxAuthenticator gxAuthenticator;

    /**
     * Initialize mock objects.
     */
    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

    	@Configuration
    	@Profile({ "disable-authn" }) 
    	class DisabledConfig {
    		@Bean
    		public PiazzaAuthenticator piazzaAuthenticator() {
    			return null;
    		}

    		@Bean
    		public RestTemplate restTemplate() {
    			return new RestTemplate();
    		}
    	}

    	@Configuration
    	@Profile({ "geoaxis" }) 
    	class GxConfig {

    		@Value("${http.max.total}")
    		private int httpMaxTotal;

    		@Value("${http.max.route}")
    		private int httpMaxRoute;

    		@Value("${JKS_FILE}")
    		private String keystoreFileName;

    		@Value("${JKS_PASSPHRASE}")
    		private String keystorePassphrase;

    		@Value("${PZ_PASSPHRASE}")
    		private String piazzaKeyPassphrase;

    		@Bean
    		public PiazzaAuthenticator piazzaAuthenticator() {
    			return new GxAuthenticator();
    		}

    		@Bean
    		public RestTemplate restTemplate() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException,
    				KeyStoreException, CertificateException, IOException {
    			SSLContext sslContext = SSLContexts.custom().loadKeyMaterial(getStore(), piazzaKeyPassphrase.toCharArray())
    					.loadTrustMaterial(getStore(), new TrustSelfSignedStrategy()).useProtocol("TLS").build();
    			HttpClient httpClient = HttpClientBuilder.create().setMaxConnTotal(httpMaxTotal).setSSLContext(sslContext)
    					.setMaxConnPerRoute(httpMaxRoute).setSSLHostnameVerifier(new NoopHostnameVerifier())
    					.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
    						@Override
    						public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
    							HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
    							while (it.hasNext()) {
    								HeaderElement headerElement = it.nextElement();
    								String param = headerElement.getName();
    								String value = headerElement.getValue();
    								if (value != null && param.equalsIgnoreCase("timeout")) {
    									return Long.parseLong(value) * 1000;
    								}
    							}
    							return 5L * 1000;
    						}
    					}).build();

    			RestTemplate restTemplate = new RestTemplate();
    			restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient));
    			restTemplate.setMessageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter())); // Why is this
    																											// required?
    			return restTemplate;
    		}

    		protected KeyStore getStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
    			final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    			InputStream inputStream = getClass().getClassLoader().getResourceAsStream(keystoreFileName);
    			try {
    				store.load(inputStream, keystorePassphrase.toCharArray());
    			} finally {
    				inputStream.close();
    			}

    			return store;
    		}
    	}
        ReflectionTestUtils.setField(this.gxAuthenticator, "npeUsersOnly", false);
    }

    @Test
    public void testGetAuthenticationDecisionUserPass() {
        GxAuthNResponse gxResponse = new GxAuthNResponse();
        gxResponse.setSuccessful(false);

        AuthResponse notAuthenticatedResponse = testGetAuthenicationDecisionUserPass(gxResponse);
        assertFalse(notAuthenticatedResponse.getIsAuthSuccess());
    }

    @Test
    public void testGetAuthenticatiohnDecisionUserPassValid() {
        GxAuthNResponse gxResponse = new GxAuthNResponse();
        gxResponse.setSuccessful(true);

        PrincipalItem piUid = new PrincipalItem();
        piUid.setName("UID");
        piUid.setValue("my_uid_value");
        PrincipalItem piDn = new PrincipalItem();
        piDn.setName("DN");
        piDn.setValue("my_dn_value");
        ArrayList<PrincipalItem> piList = new ArrayList<>();
        piList.add(piUid);
        piList.add(piDn);

        Principal principal = new Principal();
        principal.setPrincipal(piList);
        gxResponse.setPrincipals(principal);

        ReflectionTestUtils.setField(this.gxAuthenticator, "npeUsersOnly", false);
        Mockito.when(this.userProfileClient.getUserProfileFromGx("my_uid_value", "my_dn_value"))
                .thenReturn(new UserProfile());

        AuthResponse authenticatedResponse = testGetAuthenicationDecisionUserPass(gxResponse);
        assertTrue(authenticatedResponse.getIsAuthSuccess());
    }

    private AuthResponse testGetAuthenicationDecisionUserPass(GxAuthNResponse gxResponse) {
        // Mock Gx Service Call
        ReflectionTestUtils.setField(gxAuthenticator, "gxApiUrlAtnBasic", "https://geoaxis.api.com/atnrest/basic");
        ReflectionTestUtils.setField(gxAuthenticator, "gxBasicMechanism", "GxDisAus");
        ReflectionTestUtils.setField(gxAuthenticator, "gxBasicHostIdentifier", "//OAMServlet/disaususerprotected");

        GxAuthNUserPassRequest request = new GxAuthNUserPassRequest();
        request.setUsername("bsmith");
        request.setPassword("mypass");
        request.setMechanism("GxDisAus");
        request.setHostIdentifier("//OAMServlet/disaususerprotected");

        Mockito.doReturn(gxResponse).when(restTemplate).postForObject(eq("https://geoaxis.api.com/atnrest/basic"), refEq(request),
                eq(GxAuthNResponse.class));

        return gxAuthenticator.getAuthenticationDecision("bsmith", "mypass");
    }

    @Test
    public void testGetAuthenticationDecisionPKI() {

        // Mock Gx Service Call
        ReflectionTestUtils.setField(gxAuthenticator, "gxApiUrlAtnCert", "https://geoaxis.api.com/atnrest/cert");

        String testPEMFormatted = "-----BEGIN CERTIFICATE-----\nthis\nis\njust\na\ntest\nyes\nit\nis\n-----END CERTIFICATE-----";
        String testPEM = "-----BEGIN CERTIFICATE----- this is just a test yes it is -----END CERTIFICATE-----";

        // Mock Request
        GxAuthNCertificateRequest request = new GxAuthNCertificateRequest();
        request.setPemCert(testPEMFormatted);
        request.setMechanism("GxCert");
        request.setHostIdentifier("//OAMServlet/certprotected");

        // (1) Mock Response - No PrincipalItems returned.
        Principal principal = new Principal();
        GxAuthNResponse gxResponse = new GxAuthNResponse();
        gxResponse.setSuccessful(false);
        gxResponse.setPrincipals(principal);

        // Test
        Mockito.doReturn(gxResponse).when(restTemplate).postForObject(eq("https://geoaxis.api.com/atnrest/cert"), refEq(request),
                eq(GxAuthNResponse.class));
        boolean isAuthenticated = gxAuthenticator.getAuthenticationDecision(testPEM).getIsAuthSuccess();
        UserProfile profile = gxAuthenticator.getAuthenticationDecision(testPEM).getUserProfile();

        // Verify
        assertFalse(isAuthenticated);
        assertNull(profile);

        // (2) Mock Response - UID returned
        PrincipalItem principalItem = new PrincipalItem();
        principalItem.setName("UID");
        principalItem.setValue("testuser");
        UserProfile mockProfile = new UserProfile();
        mockProfile.setUsername("testuser");
        when(accessor.getUserProfileByApiKey(Mockito.eq("UID"))).thenReturn(mockProfile);
        when(accessor.getUserProfileByUsername(Mockito.any())).thenReturn(mockProfile);

        principal.setPrincipal(Arrays.asList(principalItem));
        gxResponse.setPrincipals(principal);

        // Test
        Mockito.doReturn(gxResponse).when(restTemplate).postForObject(eq("https://geoaxis.api.com/atnrest/cert"), refEq(request),
                eq(GxAuthNResponse.class));
        isAuthenticated = gxAuthenticator.getAuthenticationDecision(testPEM).getIsAuthSuccess();

        // Verify
        assertFalse(isAuthenticated);

        // (3) Mock Response - PrincipalItems with no UID returned.
        principalItem = new PrincipalItem();
        principalItem.setName("CN");
        principalItem.setValue("a CN string");

        principal.setPrincipal(Arrays.asList(principalItem));
        gxResponse.setPrincipals(principal);

        // Test
        Mockito.doReturn(gxResponse).when(restTemplate).postForObject(eq("https://geoaxis.api.com/atnrest/cert"), refEq(request),
                eq(GxAuthNResponse.class));
        isAuthenticated = gxAuthenticator.getAuthenticationDecision(testPEM).getIsAuthSuccess();
        profile = gxAuthenticator.getAuthenticationDecision(testPEM).getUserProfile();

        // Verify
        assertFalse(isAuthenticated);
        assertNull(profile);
    }
}