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
package org.venice.piazza.servicecontroller.async;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import model.service.async.AsyncServiceInstance;

/**
 * Tests Async Scheduler
 * 
 * @author Patrick.Doody
 *
 */
public class AsyncSchedulerTest {
	@Mock
	private DatabaseAccessor accessor;
	@Mock
	private AsynchronousServiceWorker worker;
	@InjectMocks
	private AsyncServiceInstanceScheduler scheduler;

	private AsyncServiceInstance mockInstance = new AsyncServiceInstance();

	/**
	 * Test initialization
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(scheduler, "STALE_INSTANCE_THRESHOLD_SECONDS", 5);
		ReflectionTestUtils.setField(scheduler, "POLL_FREQUENCY_SECONDS", 5);
	}

	/**
	 * Tests polling of stale async instances.
	 */
	@Test
	public void testPolling() throws Exception {
		// Mock
		Mockito.doReturn(new ArrayList<AsyncServiceInstance>(Arrays.asList(mockInstance))).when(accessor).getStaleServiceInstances();

		// Start Polling
		scheduler.startPolling();

		// Wait a couple of seconds on the main thread, then cancel.
		Thread.sleep(1000); //NOSONAR
		scheduler.stopPolling();
	}

	/**
	 * Tests the cancelling of an Instance
	 */
	@Test
	public void testCancel() {
		// Mock
		Mockito.doReturn(mockInstance).when(accessor).getInstanceByJobId(Mockito.eq("123456"));

		// Test
		scheduler.cancelInstance("123456");
	}
}
