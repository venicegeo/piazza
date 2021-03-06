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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.ArrayList;

import javax.management.remote.JMXPrincipal;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;

import exception.PiazzaJobException;

import org.venice.piazza.access.controller.AccessController;
import org.venice.piazza.gateway.controller.DataController;
import org.venice.piazza.gateway.controller.util.GatewayUtil;
import org.venice.piazza.ingest.controller.IngestController;

import model.data.DataResource;
import model.data.type.GeoJsonDataType;
import model.data.type.TextDataType;
import model.job.metadata.ResourceMetadata;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.response.DataResourceListResponse;
import model.response.DataResourceResponse;
import model.response.ErrorResponse;
import model.response.JobResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Data Controller, and various Data Access/Load Jobs.
 * 
 * @author Patrick.Doody
 * 
 */
public class DataTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private GatewayUtil gatewayUtil;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private AmazonS3 s3Client;
	@Mock
	private AccessController accessController;
	@Mock
	private IngestController ingestController;
	
	@InjectMocks
	private DataController dataController;

	private Principal user;
	private DataResource mockData;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		MockitoAnnotations.initMocks(gatewayUtil);

		// Mock some Data that we can use in our test cases.
		mockData = new DataResource();
		mockData.dataId = "DataID";
		mockData.dataType = new TextDataType();
		((TextDataType) mockData.dataType).content = "MockData";
		mockData.metadata = new ResourceMetadata();
		mockData.metadata.setName("Test Data");

		// Mock a user
		user = new JMXPrincipal("Test User");

		when(gatewayUtil.getErrorResponse(anyString())).thenCallRealMethod();
	}

	/**
	 * Test GET /data endpoint
	 */
	@Test
	public void testGetData() {
		// When the Gateway asks Access for a List of Data, Mock that
		// response here.
		DataResourceListResponse mockResponse = new DataResourceListResponse();
		mockResponse.data = new ArrayList<DataResource>();
		mockResponse.getData().add(mockData);
		mockResponse.pagination = new Pagination(new Long(1), 0, 10, "test", "asc");
		when(accessController.getAllData(eq("123456"), eq(0), eq(10), eq("sortby"), eq("order"), eq("keyword"), eq("createdby")))
				.thenReturn(new ResponseEntity<PiazzaResponse>(mockResponse, HttpStatus.OK));

		// Get the data
		ResponseEntity<PiazzaResponse> entity = dataController.getData("keyword", "123456", 0, 10, "order", "sortby", "createdby", user);
		PiazzaResponse response = entity.getBody();

		// Verify the results
		assertTrue(response instanceof DataResourceListResponse);
		DataResourceListResponse dataList = (DataResourceListResponse) response;
		assertTrue(dataList.getData().size() == 1);
		assertTrue(dataList.getPagination().getCount() == 1);
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
	}
	
	/**
	 * Test exception during GET /data endpoint
	 */
	@Test
	public void testGetData_Error() {
		// Mock an Exception being thrown and handled.
		when(accessController.getAllData(eq("123456"), eq(0), eq(10), eq("sortby"), eq("order"), eq("keyword"), eq("createdby")))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Get the data
		ResponseEntity<PiazzaResponse> entity = dataController.getData("keyword", "123456", 0, 10, "order", "sortby", "createdby", user);
		PiazzaResponse response = entity.getBody();

		// Verify that a proper exception was thrown.
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	
	
	/**
	 * Test POST /data endpoint
	 */
	@Test
	public void testAddData() throws Exception {
		// Mock an Ingest Job request, containing some sample data we want to
		// ingest.
		IngestJob mockJob = new IngestJob();
		mockJob.data = mockData;
		mockJob.host = false;

		// Generate a UUID that we can reproduce.
		when(gatewayUtil.sendJobRequest(any(PiazzaJobRequest.class), anyString())).thenReturn("123456");

		// Submit a mock request
		ResponseEntity<PiazzaResponse> entity = dataController.ingestData(mockJob, user);
		PiazzaResponse response = entity.getBody();

		// Verify the results. If the mock message is sent, then this is
		// considered a success.
		assertTrue(response instanceof JobResponse == true);
		assertTrue(response instanceof ErrorResponse == false);
		assertTrue(((JobResponse) response).data.getJobId().equalsIgnoreCase("123456"));
		assertTrue(entity.getStatusCode().equals(HttpStatus.CREATED));

		// Test an Exception
		when(gatewayUtil.sendJobRequest(any(PiazzaJobRequest.class), anyString())).thenThrow(new PiazzaJobException("Error"));
		entity = dataController.ingestData(mockJob, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test POST /data/file endpoint
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAddFile() throws Exception {
		// Mock an Ingest Job request, containing some sample data we want to
		// ingest. Also mock a file.
		IngestJob mockJob = new IngestJob();
		mockJob.data = mockData;
		mockJob.host = false; // This will cause a failure initially
		MockMultipartFile file = new MockMultipartFile("test.tif", "Content".getBytes());

		// Generate a UUID that we can reproduce.
		when(gatewayUtil.sendJobRequest(any(PiazzaJobRequest.class), anyString())).thenReturn("123456");

		// Test the request
		ResponseEntity<PiazzaResponse> entity = dataController.ingestDataFile(new ObjectMapper().writeValueAsString(mockJob), file, user);
		PiazzaResponse response = entity.getBody();

		// Verify the results. This request should fail since the host flag is
		// set to false.
		assertTrue(response instanceof ErrorResponse == true);

		mockJob.host = true;
		// Resubmit the Job. Now it should fail because it is a TextResource.
		entity = dataController.ingestDataFile(new ObjectMapper().writeValueAsString(mockJob), file, user);
		response = entity.getBody();

		assertTrue(response instanceof ErrorResponse == true);

		// Change to a File that should succeed.
		mockJob.data = new DataResource();
		mockJob.data.dataType = new GeoJsonDataType();

		// Resubmit the Job. It should now succeed with the message successfully
		// being sent to the Message Bus.
		entity = dataController.ingestDataFile(new ObjectMapper().writeValueAsString(mockJob), file, user);
		response = entity.getBody();

		assertTrue(response instanceof ErrorResponse == false);
		assertTrue(((JobResponse) response).data.getJobId().equalsIgnoreCase("123456"));
		assertTrue(entity.getStatusCode().equals(HttpStatus.CREATED));
	}

	/**
	 * Test GET /data/{dataId}
	 */
	@Test
	public void testGetDataItem() {
		// Mock the Response
		DataResourceResponse mockResponse = new DataResourceResponse(mockData);
		when(accessController.getData(eq("123456")))
			.thenReturn(new ResponseEntity<PiazzaResponse>(mockResponse, HttpStatus.OK));


		// Test
		ResponseEntity<PiazzaResponse> entity = dataController.getMetadata("123456", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertFalse(response instanceof ErrorResponse);
		assertTrue(((DataResourceResponse) response).data.getDataId().equalsIgnoreCase(mockData.getDataId()));
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
	}
	
	/**
	 * Test exception during GET /data/{dataId}
	 */
	@Test
	public void testGetDataItem_Error() {
		// Mock the Response
		when(accessController.getData(eq("123456")))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
		
		// Test an Exception

		ResponseEntity<PiazzaResponse> entity = dataController.getMetadata("123456", user);
		PiazzaResponse response = entity.getBody();
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test DELETE /data/{dataId}
	 */
	@Test
	public void testDeleteData() {
		// Mock the Response
		when(ingestController.deleteData("123456"))
				.thenReturn(new ResponseEntity<PiazzaResponse>(new SuccessResponse("Deleted", "Ingest"), HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = dataController.deleteData("123456", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertTrue(response instanceof ErrorResponse == false);
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
	}
	
	/**
	 * Test exception during DELETE /data/{dataId}
	 */
	@Test
	public void testDeleteData_Error() {
		// Mock the Response
		when(ingestController.deleteData("123456"))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<PiazzaResponse> entity = dataController.deleteData("123456", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test PUT /data/{dataId}
	 */
	@Test
	public void testUpdateData() {
		// Mock
		when(ingestController.updateMetadata("123456", mockData.getMetadata()))
				.thenReturn(new ResponseEntity<PiazzaResponse>(new SuccessResponse("Updated", "Ingest"), HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = dataController.updateMetadata("123456", mockData.getMetadata(), user);

		// Verify
		assertTrue(entity.getBody() instanceof SuccessResponse);
	}

	/**
	 * Test exception during PUT /data/{dataId}
	 */
	@Test
	public void testUpdateData_Error() {
		// Mock
		when(ingestController.updateMetadata("123456", mockData.getMetadata()))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test
		ResponseEntity<PiazzaResponse> entity = dataController.updateMetadata("123456", mockData.getMetadata(), user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /file/{dataId}
	 */
	@Test
	public void testDownload() throws Exception {
		// Mock
		ResponseEntity<byte[]> mockResponse = new ResponseEntity<byte[]>("Content".getBytes(), HttpStatus.OK);
		when(accessController.accessFile("123456", "test.txt"))
			.thenReturn(mockResponse);

		// Test
		ResponseEntity<?> entity = dataController.getFile("123456", "test.txt", user);
		byte[] response = (byte[]) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertEquals("Content", new String(response));
	}
	
	/**
	 * Test exception during GET /file/{dataId}
	 */
	@Test
	public void testDownload_Error() throws Exception {
		// Mock
		when(accessController.accessFile("123456", "test.txt"))
			.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		// Test+Verify
		ResponseEntity<?> entity = dataController.getFile("123456", "test.txt", user);
		PiazzaResponse response = (PiazzaResponse) entity.getBody();

		// Verify
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}
}