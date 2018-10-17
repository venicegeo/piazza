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
package org.venice.piazza.servicecontroller.messaging.handlers;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import model.job.PiazzaJobType;
import model.job.type.UpdateServiceJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.service.metadata.Service;
import util.PiazzaLogger;

/**
 * Handler for handling registerService requests. This handler is used when register-service topics are received
 * or when clients utilize the ServiceController registerService web service.
 * 
 * @author mlynum
 * @version 1.0
 *
 */
@Component
public class UpdateServiceHandler implements PiazzaJobHandler {

	@Autowired
	private DatabaseAccessor accessor;

	@Autowired
	private PiazzaLogger coreLogger;

	private static final Logger LOG = LoggerFactory.getLogger(UpdateServiceHandler.class);

	/**
	 * Handler for the RegisterServiceJob that was submitted. Stores the metadata in DB
	 * 
	 * @see org.venice.piazza.servicecontroller.messaging.handlers.Handler#handle(model.job.PiazzaJobType)
	 */
	public ResponseEntity<String> handle(PiazzaJobType jobRequest) {

		LOG.debug("Updating a service");
		UpdateServiceJob job = (UpdateServiceJob) jobRequest;
		if (job != null) {
			// Get the ResourceMetadata
			Service sMetadata = job.getData();
			LOG.info(String.format("ServiceMetadata received is %s", sMetadata.toString()));
			coreLogger.log("serviceMetadata received is " + sMetadata, Severity.INFORMATIONAL);
			String result = handle(sMetadata);

			if (result.length() > 0) {
				String jobId = job.getJobId();
				// TODO Use the result, send a message with the resource Id and jobId
				ArrayList<String> resultList = new ArrayList<>();
				resultList.add(jobId);
				resultList.add(sMetadata.getServiceId());

				return new ResponseEntity<>(resultList.toString(), HttpStatus.OK);

			} else {
				coreLogger.log("No result response from the handler, something went wrong", Severity.ERROR);
				return new ResponseEntity<>("UpdateServiceHandler handle didn't work", HttpStatus.UNPROCESSABLE_ENTITY);
			}
		} else {
			coreLogger.log("A null PiazzaJobRequest was passed in. Returning null", Severity.ERROR);
			return new ResponseEntity<>("A Null PiazzaJobRequest was received", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * 
	 * @param rMetadata
	 * @return resourceId of the registered service
	 */
	public String handle(Service sMetadata) {
		String result = "";
		try {
			if (sMetadata != null) {
				coreLogger.log(String.format("Updating a registered service with ID %s", sMetadata.getServiceId()), Severity.INFORMATIONAL);

				result = accessor.updateService(sMetadata);

				if (result.length() > 0) {
					coreLogger.log("The service " + sMetadata.getResourceMetadata().name + " was updated with id " + result,
							Severity.INFORMATIONAL);

					coreLogger.log(String.format("Service was updated %s", sMetadata.getServiceId()), Severity.INFORMATIONAL,
							new AuditElement("serviceController", "updatedRegisteredService", sMetadata.getServiceId()));
				} else {
					coreLogger.log("The service " + sMetadata.getResourceMetadata().name + " was NOT updated", Severity.INFORMATIONAL);
					coreLogger.log("The service was NOT updated", Severity.ERROR,
							new AuditElement("serviceController", "failedToUpdateService", sMetadata.getServiceId()));
				}
			}
		} catch (IllegalArgumentException ex) {
			LOG.error("IllegalArgumentException occurred", ex);
			coreLogger.log(ex.getMessage(), Severity.ERROR);
		}

		return result;
	}
}