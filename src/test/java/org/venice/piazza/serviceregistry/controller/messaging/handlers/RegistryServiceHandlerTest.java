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
package org.venice.piazza.serviceregistry.controller.messaging.handlers;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;


import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;
import org.venice.piazza.servicecontroller.messaging.handlers.RegisterServiceHandler;
import org.venice.piazza.servicecontroller.util.CoreServiceProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import model.data.DataType;
import model.data.type.URLParameterDataType;
import model.job.metadata.ResourceMetadata;
import model.response.ServiceResponse;

import model.service.metadata.Service;
import util.PiazzaLogger;
import util.UUIDFactory;

@RunWith(PowerMockRunner.class)
public class RegistryServiceHandlerTest {
	
	@Mock
	private RegisterServiceHandler rsHandler;
	
	RestTemplate template = null;
	Service service = null;
	ResourceMetadata rm  = null;
	UUIDFactory uuidFactory = null;
	@Before
	public void setup() {
	    uuidFactory = mock(UUIDFactory.class);
	    when(uuidFactory.getUUID()).thenReturn("NoDoz");
		template = mock(RestTemplate.class);
		try {
			whenNew(RestTemplate.class).withNoArguments().thenReturn(template);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		rm = new ResourceMetadata();
		rm.name = "toUpper Params";
		rm.description = "Service to convert string to uppercase";
		service = new Service();
		service.setMethod("POST");
		service.setResourceMetadata(rm);
		service.setServiceId("8");
		DataType dataType1 = new URLParameterDataType();
		
		//DEBUGGING
		ObjectMapper mapper = new ObjectMapper();
		try {
			String jsonReq = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(service);
			System.out.println(jsonReq);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	@PrepareForTest({RegisterServiceHandler.class})
	@Test
	@Ignore
	public void testHandleWithData() {
		DatabaseAccessor mockAccessor = mock(DatabaseAccessor.class);
		when(mockAccessor.save(service)).thenReturn("8");
		CoreServiceProperties props = mock(CoreServiceProperties.class);
		//when(props.getUuidservicehost().thenReturn("Nothing");
		PiazzaLogger logger = mock(PiazzaLogger.class);
        String retVal = rsHandler.handle(service);
        assertTrue(retVal.contains("NoDoz"));
        assertTrue(service.getServiceId().contains("NoDoz"));
		
	}
	
	@PrepareForTest({RegisterServiceHandler.class})
	@Test
	public void testHandleWithoutData() {
		DatabaseAccessor mockAccessor = mock(DatabaseAccessor.class);
		when(mockAccessor.save(service)).thenReturn("8");
		CoreServiceProperties props = mock(CoreServiceProperties.class);
		PiazzaLogger logger = mock(PiazzaLogger.class);
		Service nullService = null;
        String retVal = rsHandler.handle(nullService);
        assertNull(retVal);
	}
	
	
}
