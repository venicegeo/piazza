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
package org.venice.piazza.gateway.controller;

import java.io.InputStream;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import org.venice.piazza.gateway.controller.util.GatewayUtil;
import org.venice.piazza.gateway.controller.util.PiazzaRestController;
import org.venice.piazza.idam.controller.AuthController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import model.logger.AuditElement;
import model.logger.Severity;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.UUIDResponse;
import model.response.UserProfileResponse;
import util.PiazzaLogger;

/**
 * REST Controller that defines administrative end points that reference logging, administartion, and debugging
 * information related to the Gateway component.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@Api
public class AdminController extends PiazzaRestController {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private HttpServletRequest request;
	@Autowired
	private GatewayUtil gatewayUtil;
	@Value("${SPACE}")
	private String SPACE;
	@Value("${ingest.url}")
	private String INGEST_URL;
	@Value("${access.url}")
	private String ACCESS_URL;
	@Value("${jobmanager.url}")
	private String JOBMANAGER_URL;
	@Value("${servicecontroller.url}")
	private String SERVICECONTROLLER_URL;
	@Value("${security.url}")
	private String SECURITY_URL;
	@Value("${release.url}")
	private String RELEASE_URL;


	@Autowired
	private AuthController idamAuthController;
	@Autowired
	private org.venice.piazza.idam.controller.AdminController idamAdminController;

	private static final Logger LOG = LoggerFactory.getLogger(AdminController.class);

	private static final String GATEWAY = "Gateway";
	private static final String AUTHORIZATION = "Authorization";

	/**
	 * Healthcheck required for all Piazza Core Services
	 * 
	 * @return String
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ApiOperation(hidden = true, value = "Health Check")
	public String getHealthCheck() {
		return "Hello, Health Check here for pz-gateway.";
	}

	/**
	 * This will first attempt to locate the version number from local pz-release.json file. If this file does not
	 * exist, then it will point to the pz-release running application.
	 * 
	 * @return Version information for Gateway and all sub-components
	 */
	@ApiOperation(hidden = true, value = "Version")
	@RequestMapping(value = "/version", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getVersion() {
		try {
			InputStream templateStream = getClass().getClassLoader().getResourceAsStream("pz-release.json");
			String localVersionJson = IOUtils.toString(templateStream);
			return new ResponseEntity<String>(localVersionJson, HttpStatus.OK);
		} catch (Exception exception) {
			String error = String.format("Error retrieving version for Piazza: %s", exception.getMessage());
			LOG.error(error, exception);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns administrative statistics for this Gateway component.
	 * 
	 * @return Component information
	 */
	@ApiOperation(hidden = true, value = "Administrative Statistics")
	@RequestMapping(value = "/admin/stats", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getAdminStats() {
		Map<String, Object> stats = new HashMap<String, Object>();
		// Write the URL configs
		stats.put("Space", SPACE);
		stats.put("Ingest", INGEST_URL);
		stats.put("Access", ACCESS_URL);
		stats.put("JobManager", JOBMANAGER_URL);
		stats.put("ServiceController", SERVICECONTROLLER_URL);
		stats.put("Security", SECURITY_URL);
		stats.put("Release", RELEASE_URL);
		// Return
		return new ResponseEntity<Map<String, Object>>(stats, HttpStatus.OK);
	}

	/**
	 * Generates a new API Key for a user based on their credentials. Accepts username/password or PKI Cert for GeoAxis.
	 * 
	 * @return API Key Response information
	 */
	@ApiOperation(hidden = true, value = "Legacy API Key Generation")
	@RequestMapping(value = "/key", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getNewApiKeyV1() {
		return generateNewApiKey();
	}

	/**
	 * Generates a new API Key for a user based on their credentials. Accepts username/password or PKI Cert for GeoAxis.
	 * 
	 * @return API Key Response information
	 */
	@ApiOperation(value = "Create new API Key", notes = "Creates a new Piazza API Key based on the Authentication block. This creates a new Key and overrides any previous keys.", tags = "User")
	@RequestMapping(value = "/v2/key", method = RequestMethod.POST)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "API Key information.", response = UUIDResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getNewApiKeyV2() {
		return generateNewApiKey();
	}

	/**
	 * Gets the existing API Key for the user.
	 * 
	 * @return API Key information
	 */
	@ApiOperation(value = "Get Current API Key", notes = "Gets the current API Key based on the provided Authentication block. Will not create a new key.", tags = "User")
	@RequestMapping(value = "/v2/key", method = RequestMethod.GET)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "API Key information.", response = UUIDResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getExistingApiKeyV2() {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set(AUTHORIZATION, request.getHeader(AUTHORIZATION));
			try {
				return idamAuthController.generateApiKeyV2();
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error(hee.getResponseBodyAsString(), hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error retrieving API Key: %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the User Profile for the current user (extracted from Auth API Key)
	 * 
	 * @return User Profile
	 */
	@ApiOperation(value = "Get Current User Profile", notes = "Gets the User Profile for the user based on provided API Key from Authentication block.", tags = "User")
	@RequestMapping(value = "/profile", method = RequestMethod.GET)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "User Profile information.", response = UserProfileResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getUserProfile(Principal user) {
		try {
			// Audit Request
			String username = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s requested Self User Profile Information.", dn), Severity.INFORMATIONAL,
					new AuditElement(dn, "requestUserProfile", username));
			// Broker to IDAM
			try {
				ResponseEntity<PiazzaResponse> response = idamAdminController.getUserProfile(username);
				logger.log(String.format("User %s successfully retrieved User Profile.", username), Severity.INFORMATIONAL,
						new AuditElement(dn, "successUserProfile", username));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error querying User Profile.", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error retrieving User Profile : %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Generates the API Key from the Pz-Idam endpoint.
	 * 
	 * @return API Key response information.
	 */
	private ResponseEntity<PiazzaResponse> generateNewApiKey() {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set(AUTHORIZATION, request.getHeader(AUTHORIZATION));
			try {
				return idamAuthController.generateApiKeyV2();
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error(hee.getResponseBodyAsString(), hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error retrieving API Key: %s", exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
