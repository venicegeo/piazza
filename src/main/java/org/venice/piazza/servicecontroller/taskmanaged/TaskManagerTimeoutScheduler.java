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
package org.venice.piazza.servicecontroller.taskmanaged;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import model.logger.Severity;
import model.service.metadata.Service;
import model.service.taskmanaged.ServiceJob;
import util.PiazzaLogger;

/**
 * Scheduling class that manages the constant polling for timeouts of Service Jobs for Task-Managed Services.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class TaskManagerTimeoutScheduler {
	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private ServiceTaskManager serviceTaskManager;
	@Autowired
	private PiazzaLogger piazzaLogger;

	@Value("${task.managed.timeout.frequency.seconds}")
	private int POLL_FREQUENCY_SECONDS; //NOSONAR

	private CheckTimeoutTask timeoutTask = new CheckTimeoutTask();
	private Timer pollTimer = new Timer();

	/**
	 * Begins scheduled polling of timed out Service Jobs.
	 */
	@PostConstruct
	public void startPolling() {
		// Begin polling at the determined frequency
		pollTimer.schedule(timeoutTask, 10000, POLL_FREQUENCY_SECONDS * (long) 1000);
	}

	/**
	 * Halts scheduled polling of timed out Service Jobs.
	 */
	public void stopPolling() {
		pollTimer.cancel();
	}

	/**
	 * Timer Task that will, on a schedule, poll for the Status of Stale Service Jobs that have timed out.
	 */
	public class CheckTimeoutTask extends TimerTask {
		/**
		 * Polls all stale Service Jobs for all Task-Managed user Services.
		 */
		@Override
		public void run() {
			piazzaLogger.log("Checking for Timed out Service Jobs for Task-Managed Services.", Severity.INFORMATIONAL);
			// Get a list of all Task-Managed User Services
			List<Service> managedServices = accessor.getTaskManagedServices();
			// For each Task Managed Service, query for stale Jobs.
			for (Service managedService : managedServices) {
				// Get the list of all Stale Jobs
				List<ServiceJob> staleJobs = accessor.getTimedOutServiceJobs(managedService.getServiceId());
				for (ServiceJob staleJob : staleJobs) {
					// For each Stale Job, process its timeout.
					serviceTaskManager.processTimedOutServiceJob(managedService.getServiceId(), staleJob);
				}
			}
		}
	}
}
