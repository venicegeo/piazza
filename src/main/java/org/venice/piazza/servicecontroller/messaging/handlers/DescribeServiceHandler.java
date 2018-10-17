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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.venice.piazza.servicecontroller.data.accessor.DatabaseAccessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import model.job.PiazzaJobType;
import model.job.type.DescribeServiceMetadataJob;
import model.logger.Severity;
import model.service.metadata.Service;
import util.PiazzaLogger;

/**
 * @author mlynum
 * @version 1.0
 */

@Component
public class DescribeServiceHandler implements PiazzaJobHandler { 
	private static final Logger LOG = LoggerFactory.getLogger(DescribeServiceHandler.class);
	
	@Autowired
	private DatabaseAccessor accessor;
	@Autowired
	private PiazzaLogger coreLogger;
	
	/**
	 * Describe service handler
	 */
	public ResponseEntity<String> handle(PiazzaJobType jobRequest) {
		coreLogger.log("Describing a service", Severity.INFORMATIONAL);
		DescribeServiceMetadataJob job = (DescribeServiceMetadataJob) jobRequest;
		if (job != null ) {
			ResponseEntity<String> handleResourceReturn = handle(job.getServiceID());
	        if (handleResourceReturn.getBody().length() > 0) {
	        	return new ResponseEntity<>(handleResourceReturn.getBody(), handleResourceReturn.getStatusCode());
			} else {
				coreLogger.log("No result response from the handler, something went wrong", Severity.ERROR);
				return new ResponseEntity<>("Could not retrieve service metadata.", HttpStatus.NOT_FOUND);
			}
		} else {
			coreLogger.log("No DescribeServiceMetadataJob provided.", Severity.ERROR);
			return new ResponseEntity<>("No DescribeServiceMetadataJob provided.", HttpStatus.BAD_REQUEST);
		}
	}

	public ResponseEntity<String> handle(String serviceId) {
		ResponseEntity<String> responseEntity = null;

		try {
			Service sMetadata = accessor.getServiceById(serviceId);
			ObjectMapper mapper = new ObjectMapper();
			String result = mapper.writeValueAsString(sMetadata);
			responseEntity = new ResponseEntity<>(result, HttpStatus.OK);
		} catch (JsonProcessingException ex) {
			LOG.error("Could not retrieve resourceId", ex);
			coreLogger.log("Could not retrieve resourceId " + serviceId, Severity.ERROR);
			responseEntity = new ResponseEntity<>("Could not retrieve resourceId " + serviceId, HttpStatus.NOT_FOUND);
		}

		return responseEntity;
	}
}
