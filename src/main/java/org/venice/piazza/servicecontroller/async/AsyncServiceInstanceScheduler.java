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

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import model.service.async.AsyncServiceInstance;

/**
 * This component manages the polling cycle of Asynchronous Service Instances.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class AsyncServiceInstanceScheduler {
	@Value("${async.stale.instance.threshold.seconds}")
	private int STALE_INSTANCE_THRESHOLD_SECONDS; //NOSONAR
	@Value("${async.poll.frequency.seconds}")
	private int POLL_FREQUENCY_SECONDS; //NOSONAR

	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private AsynchronousServiceWorker worker;

	private PollServiceTask pollTask = new PollServiceTask();
	private Timer pollTimer = new Timer();

	/**
	 * Begins scheduled polling of asynchronous job status.
	 */
	@PostConstruct
	public void startPolling() {
		// Begin polling at the determined frequency
		pollTimer.schedule(pollTask, 10000, POLL_FREQUENCY_SECONDS * (long) 1000);
	}

	/**
	 * Halts scheduled polling of asynchronous job status.
	 */
	public void stopPolling() {
		pollTimer.cancel();
	}

	/**
	 * Cancels a running Instance by removing it from the persistence table.
	 * 
	 * @param jobId
	 *            The Piazza Job ID of the Instance to be cancelled
	 */
	public void cancelInstance(String jobId) {
		// Get the Instance to cancel
		AsyncServiceInstance instance = accessor.getInstanceByJobId(jobId);
		if (instance != null) {
			// Handle the cancellation
			worker.sendCancellationStatus(instance);
		}
	}

	/**
	 * Timer Task that will, on a schedule, poll for the Status of Stale asynchronous user services.
	 * <p>
	 * This component is responsible for polling the status of asynchronous user service instances. It will use the
	 * AsyncServiceInstances collection in order to store persistence related to each running instance of an
	 * asynchronous user service. This component will poll each instance, at a regular interval, and make a note of its
	 * status and query time.
	 * </p>
	 */
	public class PollServiceTask extends TimerTask {
		/**
		 * Polls all stale Asynchronous Service Instances (instances that haven't been checked since the last threshold
		 * time)
		 */
		@Override
		public void run() {
			// Get the list of all stale User Services and poll each.
			List<AsyncServiceInstance> staleInstances = accessor.getStaleServiceInstances();
			for (AsyncServiceInstance instance : staleInstances) {
				worker.pollStatus(instance);
			}
		}
	}
}
