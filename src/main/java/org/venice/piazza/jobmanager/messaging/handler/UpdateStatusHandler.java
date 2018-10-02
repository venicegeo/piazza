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
package org.venice.piazza.jobmanager.messaging.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.venice.piazza.jobmanager.database.DatabaseAccessor;
import model.logger.AuditElement;
import model.logger.Severity;
import model.status.StatusUpdate;
import util.PiazzaLogger;

/**
 * Handles the request for Updating the Status of a Job in the Job Table
 * 
 * @author Patrick.Doody
 * 
 */
@Component
public class UpdateStatusHandler {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	ObjectMapper mapper;

	private static final Logger LOG = LoggerFactory.getLogger(UpdateStatusHandler.class);
	
	public void process(StatusUpdate statusUpdate) {
		// Changing the Status in the Job Table
		String jobId = statusUpdate.getJobId();
		try {
			// Update
			accessor.updateJobStatus(jobId, statusUpdate);
			// Log success
			logger.log(String.format("Processed Update Status for Job %s with Status %s.", jobId, statusUpdate.getStatus()),
					Severity.INFORMATIONAL, new AuditElement("jobmanager", "updatedJobStatus", jobId));
		} catch (Exception exception) {
			String error = String.format("Error Updating Status for Job %s with error %s", jobId, exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
		}
	}
}
