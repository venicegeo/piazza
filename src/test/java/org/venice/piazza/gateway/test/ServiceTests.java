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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXPrincipal;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import org.venice.piazza.gateway.controller.ServiceController;
import org.venice.piazza.gateway.controller.util.GatewayUtil;
import org.venice.piazza.servicecontroller.controller.TaskManagedController;

import model.job.metadata.ResourceMetadata;
import model.response.ErrorResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.response.ServiceIdResponse;
import model.response.ServiceJobResponse;
import model.response.ServiceListResponse;
import model.response.ServiceResponse;
import model.response.SuccessResponse;
import model.service.metadata.Service;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Piazza Service Controller Rest Controller
 * 
 * @author Patrick.Doody
 *
 */
public class ServiceTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private GatewayUtil gatewayUtil;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private org.venice.piazza.servicecontroller.controller.ServiceController serviceControllerController;
	@Mock
	private TaskManagedController taskManagedController;
	@InjectMocks
	private ServiceController serviceController;

	private Principal user;
	private Service mockService;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		MockitoAnnotations.initMocks(gatewayUtil);

		// Mock a Service to use
		mockService = new Service();
		mockService.setServiceId("123456");
		mockService.setUrl("service.com");
		mockService.setResourceMetadata(new ResourceMetadata());
		mockService.getResourceMetadata().setName("Test");

		// Mock a user
		user = new JMXPrincipal("Test User");

		when(gatewayUtil.getErrorResponse(anyString())).thenCallRealMethod();
	}

	/**
	 * Test POST /service endpoint
	 */
	@Test
	public void testRegister() {
		// Mock
		Service service = new Service();
		service.setServiceId("123456");
		ServiceIdResponse mockResponse = new ServiceIdResponse(service.getServiceId());
		when(serviceControllerController.registerService(any()))
			.thenReturn(new ResponseEntity<PiazzaResponse>(mockResponse, HttpStatus.CREATED));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.registerService(service, user);
		ServiceIdResponse response = (ServiceIdResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.CREATED));
		assertTrue(response.data.getServiceId().equals("123456"));
	}

	/**
	 * Test exception during POST /service endpoint
	 */
	@Test
	public void testRegister_Error() {
		// Mock
		Service service = new Service();
		service.setServiceId("123456");
		ServiceIdResponse mockResponse = new ServiceIdResponse(service.getServiceId());
		when(serviceControllerController.registerService(any()))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.registerService(service, user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /service/{serviceId}
	 */
	@Test
	public void testGetMetadata() {
		// Mock
		ServiceResponse mockResponse = new ServiceResponse(mockService);
		when(serviceControllerController.getServiceInfo("123456"))
			.thenReturn(new ResponseEntity<PiazzaResponse>(mockResponse, HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.getService("123456", user);
		ServiceResponse response = (ServiceResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.data.getServiceId().equalsIgnoreCase("123456"));
		assertTrue(response.data.getResourceMetadata().getName().equalsIgnoreCase("Test"));
	}
	
	/**
	 * Test exception during GET /service/{serviceId}
	 */
	@Test
	public void testGetMetadata_Error() {
		// Mock
		when(serviceControllerController.getServiceInfo("123456"))
			.thenThrow(new HttpServerErrorException(HttpStatus.NOT_FOUND));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.getService("123456", user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.NOT_FOUND));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test DELETE /service/{serviceId}
	 */
	@Test
	public void testDelete() {
		// Mock
		when(serviceControllerController.deleteService("123456"))
			.thenReturn(new ResponseEntity<String>("Deleted", HttpStatus.OK));

		// Test
		ResponseEntity<?> entity = serviceController.deleteService("123456", false, user);
		String response = (String) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.contains("Deleted"));
	}

	/**
	 * Test exception during DELETE /service/{serviceId}
	 */
	@Test
	public void testDelete_Error() {
		// Mock
		when(serviceControllerController.deleteService("123456"))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<?> entity = serviceController.deleteService("123456", false, user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	
	/**
	 * Test PUT /service/{serviceId}
	 */
	@Test
	public void testUpdateMetadata() {
		// Mock
		when(serviceControllerController.updateServiceMetadata("123456", mockService))
			.thenReturn(new ResponseEntity<PiazzaResponse>(new SuccessResponse("Yes", "Gateway"), HttpStatus.OK));
		
		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.updateService("123456", mockService, user);

		// Verify
		assertTrue(entity.getBody() instanceof SuccessResponse);
	}
	
	/**
	 * Test exception during PUT /service/{serviceId}
	 */
	@Test
	public void testUpdateMetadata_Error() {
		// Mock
		when(serviceControllerController.updateServiceMetadata("123456", mockService))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));		
		
		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.updateService("123456", mockService, user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /service/id/task
	 */
	@Test
	public void testNextJobInQueue() {
		// Mock
		ServiceJobResponse mockResponse = new ServiceJobResponse();
		mockResponse.data = mockResponse.new ServiceJobData();
		mockResponse.data.setJobId("123456");
		
		when(taskManagedController.getNextServiceJobFromQueue(anyString(), eq("123456")))
			.thenReturn(new ResponseEntity<PiazzaResponse>(mockResponse, HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.getNextJobInQueue("123456", user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(entity.getBody() instanceof ServiceJobResponse);
	}
	
	/**
	 * Test exception during GET /service/id/task
	 */
	@Test
	public void testNextJobInQueue_Error() {
		// Mock		
		when(taskManagedController.getNextServiceJobFromQueue(anyString(), eq("123456")))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.getNextJobInQueue("123456", user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}


	/**
	 * Test POST /service/{serviceId}/task/{jobId}
	 */
	@Test
	public void testUpdateJobStatus() {
		// Mock
		when(taskManagedController.updateServiceJobStatus(eq(gatewayUtil.getPrincipalName(user)), eq("serviceId"), eq("jobId"), any()))
			.thenReturn(new ResponseEntity<PiazzaResponse>(new SuccessResponse("Test", "Test"), HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.updateServiceJobStatus("serviceId", "jobId", new StatusUpdate(), user);

		// Verify
		assertTrue(entity.getBody() instanceof SuccessResponse);
	}

	/**
	 * Test exception during POST /service/{serviceId}/task/{jobId}
	 */
	@Test
	public void testUpdateJobStatus_Error() {
		// Mock
		when(taskManagedController.updateServiceJobStatus(eq(gatewayUtil.getPrincipalName(user)), eq("serviceId"), eq("jobId"), any()))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.updateServiceJobStatus("serviceId", "jobId", new StatusUpdate(), user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /service/{serviceId}/task/metadata
	 */
	@Test
	public void testServiceQueueData() {
		// Mock
		Map<String, Object> mockResponse = new HashMap<String, Object>();
		
		when(taskManagedController.getServiceQueueData(anyString(), eq("serviceId")))
			.thenReturn(new ResponseEntity<Map<String, Object>>(mockResponse, HttpStatus.OK));

		// Test
		ResponseEntity<?> entity = serviceController.getServiceQueueData("serviceId", user);

		// Verify
		assertTrue(entity.getBody() instanceof Map<?,?>);
	}
	
	/**
	 * Test exception during GET /service/{serviceId}/task/metadata
	 */
	@Test
	public void testServiceQueueData_Error() {
		// Mock
		when(taskManagedController.getServiceQueueData(anyString(), eq("serviceId")))
		.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<?> entity = serviceController.getServiceQueueData("serviceId", user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /service
	 */
	@Test
	public void testGetServices() {
		// Mock
		ServiceListResponse mockResponse = new ServiceListResponse();
		mockResponse.data = new ArrayList<Service>();
		mockResponse.getData().add(mockService);
		mockResponse.pagination = new Pagination(new Long(1), 0, 10, "test", "asc");
		
		when(serviceControllerController.getServices(0, 10, "order", "sortBy", "keyword", "createdBy"))
				.thenReturn(new ResponseEntity<PiazzaResponse>(mockResponse, HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.getServices("keyword", 0, 10, "createdBy", "order", "sortBy", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertTrue(response instanceof ServiceListResponse);
		ServiceListResponse serviceList = (ServiceListResponse) response;
		assertTrue(serviceList.getData().size() == 1);
		assertTrue(serviceList.getPagination().getCount() == 1);
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
	}
	
	/**
	 * Test exception during GET /service
	 */
	@Test
	public void testGetServices_Error() {
		// Mock
		when(serviceControllerController.getServices(0, 10, "order", "sortBy", "keyword", "createdBy"))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<PiazzaResponse> entity = serviceController.getServices("keyword", 0, 10, "createdBy", "order", "sortBy", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}
}
