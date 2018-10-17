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
package org.venice.piazza.servicecontroller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
public class ApplicationTest {
	@Mock
	private ConfigurableApplicationContext applicationContextMock;
	@Mock
	private SpringApplication springApplicationMock;
	@Before
    public void setup() {
		MockitoAnnotations.initMocks(this);
	}
	
	
	/**
	 * Purpose of this test is to test the the main application with no arguments
	 */
	@Test
	public void testMainNoArguments() {
		String[] args = new String[0];
		PowerMockito.when(springApplicationMock.run()).thenReturn(applicationContextMock);
		//Application.main(args);
	}
	
	/**
	 * Purpose of this test is to test the the main application with no arguments
	 */
	@Test
	public void testMainWithOneArgument() {
		PowerMockito.when(springApplicationMock.run()).thenReturn(applicationContextMock);
		String[] args = new String[1];
		args[0] = "true";
		//Application.main(args);
		
	}
}
