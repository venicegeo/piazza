/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package org.venice.piazza.gateway.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import org.venice.piazza.gateway.auth.ExtendedRequestDetails;
import org.venice.piazza.gateway.auth.UserDetailsBean;
import org.venice.piazza.idam.controller.AuthController;

import model.response.AuthResponse;
import model.security.authz.UserProfile;
import util.PiazzaLogger;

public class UserDetailsBeanTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private AuthController authController;
	@InjectMocks
	private UserDetailsBean userDetails;


	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testAuthentication() {
		// Mock
		UserProfile mockProfile = new UserProfile();
		mockProfile.setDistinguishedName("TestDN");
		mockProfile.setUsername("Tester");
		AuthResponse mockResponse = new AuthResponse(true, mockProfile);
		Mockito.when(authController.authenticateApiKey(Mockito.any()))
			.thenReturn(new ResponseEntity<AuthResponse>(mockResponse, HttpStatus.OK));
		// Test
		AuthResponse response = userDetails.getAuthenticationDecision("apiKey123");

		// Verify
		assertNotNull(response);
		assertEquals(response.getIsAuthSuccess(), true);
	}

	@Test
	public void testFullAuthorization() {
		// Mock
		MockHttpServletRequest mockRequest = new MockHttpServletRequest();
		mockRequest.setMethod("GET");
		mockRequest.setRequestURI("testUri");
		ExtendedRequestDetails mockDetails = new ExtendedRequestDetails(mockRequest);
		UserProfile mockProfile = new UserProfile();
		mockProfile.setDistinguishedName("TestDN");
		mockProfile.setUsername("Tester");
		AuthResponse mockResponse = new AuthResponse(true, mockProfile);
		Mockito.when(authController.authenticateAndAuthorize(Mockito.any()))
			.thenReturn(new ResponseEntity<AuthResponse>(mockResponse, HttpStatus.OK));
		// Test
		AuthResponse response = userDetails.getFullAuthorizationDecision("apiKey123", mockDetails);

		// Verify
		assertNotNull(response);
		assertEquals(response.isAuthSuccess, true);
		assertEquals(response.getUserProfile().getDistinguishedName(), mockProfile.getDistinguishedName());
	}
}
