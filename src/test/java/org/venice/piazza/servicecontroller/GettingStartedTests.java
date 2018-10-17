/*******************************************************************************
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
 *******************************************************************************/
package org.venice.piazza.servicecontroller;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.venice.piazza.servicecontroller.controller.GettingStartedController;
import org.venice.piazza.servicecontroller.data.model.Message;

import junit.framework.Assert;
import util.PiazzaLogger;

/**
 * Testing tutorial controller logic
 * 
 * @author Patrick.Doody
 *
 */
public class GettingStartedTests {
	@Mock
	private PiazzaLogger logger;
	@InjectMocks
	private GettingStartedController controller;

	/**
	 * Setup
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

	}

	/**
	 * Test case endpoints
	 */
	@Test
	public void testCase() {
		String result = controller.convertStringtoUpper("test");
		assertTrue(!result.isEmpty());
		result = controller.convertStringtoLower("TEST");
		assertTrue(!result.isEmpty());
	}

	/**
	 * Tests conversion endpoint
	 */
	@Test
	public void testConvert() {
		// Mock
		Message mockMessage = new Message();
		mockMessage.settheString("Test");
		mockMessage.setConversionType(Message.LOWER);

		// Test
		String result = controller.convert(mockMessage);
		assertTrue(!result.isEmpty());

		mockMessage.setConversionType(Message.UPPER);
		result = controller.convert(mockMessage);
		assertTrue(!result.isEmpty());
	}

	/**
	 * Tests quote example service
	 */
	@Test
	public void testWelcome() {
		String response = controller.movieWelcome("tester");
		assertTrue(response.contains("tester"));
		response = controller.movieWelcomeParms("tester");
		assertTrue(response.contains("tester"));
	}

}
