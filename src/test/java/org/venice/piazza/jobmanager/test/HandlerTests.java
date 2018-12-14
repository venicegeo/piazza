/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package org.venice.piazza.jobmanager.test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.UUID;

import exception.PiazzaJobException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.venice.piazza.jobmanager.database.JobManagerDatabaseAccessor;
import org.venice.piazza.jobmanager.messaging.handler.AbortJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RepeatJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RequestJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.UpdateStatusHandler;
import model.job.Job;
import model.job.JobProgress;
import model.job.result.type.TextResult;
import model.job.type.AbortJob;
import model.job.type.IngestJob;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import model.status.StatusUpdate;

import org.springframework.web.client.ResourceAccessException;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Job Handlers
 *
 * @author Patrick.Doody
 */
public class HandlerTests {
    @Mock
    private PiazzaLogger logger;
    @Mock
    private JobManagerDatabaseAccessor accessor;
    @Mock
    private UUIDFactory uuidFactory;
    @Mock
    private RequestJobHandler requestJobHandler;

    @InjectMocks
    private AbortJobHandler abortJobHandler;
    @InjectMocks
    private RepeatJobHandler repeatJobHandler;
    @InjectMocks
    private UpdateStatusHandler updateJobHandler;

    private Job mockJob;
    private PiazzaJobRequest mockAbortRequest;
    private PiazzaJobRequest repeatJobRequest;

    /**
     * Initialize Mock objects.
     */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Mock a Job
        mockJob = new Job();
        mockJob.setJobId(UUID.randomUUID().toString());
        mockJob.setStatus(StatusUpdate.STATUS_RUNNING);
        mockJob.setProgress(new JobProgress(75));
        mockJob.setJobType(new IngestJob());

        // Mock Requests
        mockAbortRequest = new PiazzaJobRequest();
        mockAbortRequest.jobType = new AbortJob("123456");
        mockAbortRequest.createdBy = "A";

        repeatJobRequest = new PiazzaJobRequest();
        repeatJobRequest.createdBy = "A";
        repeatJobRequest.jobType = new RepeatJob("123456");

        //The InjectMocks annotation doesn't seem to be setting this field.
        //ReflectionTestUtils.setField(repeatJobHandler, "requestJobHandler", this.requestJobHandler);
    }

    /**
     * Tests abort job handler with no Job found
     */
    @Test(expected = Exception.class)
    public void testAbortJobEmpty() throws Exception {
        // Mock
        when(accessor.getJobById(eq("123456"))).thenReturn(null);

        // Test when no Job is found
        abortJobHandler.process(mockAbortRequest);
    }

    /**
     * Tests when a user cannot abort someone elses Job
     */
    @Test(expected = Exception.class)
    public void testAbortPermissionError() throws Exception {
        // Mock
        Job mockCancelJob = new Job();
        mockCancelJob.setCreatedBy("B");
        when(accessor.getJobById(eq("123456"))).thenReturn(mockCancelJob);

        // Test
        abortJobHandler.process(mockAbortRequest);
    }

    /**
     * Tests the cancelling of an already aborted Job
     */
    public void testAbortCancelledJob() throws Exception {
        // Mock
        Job mockCancelJob = new Job();
        mockCancelJob.setCreatedBy("A");
        mockCancelJob.setStatus(StatusUpdate.STATUS_CANCELLED);
        when(accessor.getJobById(eq("123456"))).thenReturn(mockCancelJob);

        // Test
        abortJobHandler.process(mockAbortRequest);
    }

    /**
     * Tests aborting a Job, no errors.
     */
    @Test
    public void testAbortJob() throws Exception {
        // Mock
        Job mockCancelJob = new Job();
        mockCancelJob.setCreatedBy("A");

        when(accessor.getJobById(eq("123456"))).thenReturn(mockCancelJob);
        Mockito.doNothing().when(accessor).updateJobStatus(eq("123456"), eq(StatusUpdate.STATUS_CANCELLED));

        // Test several cases for coverage.
        mockCancelJob.setStatus(StatusUpdate.STATUS_RUNNING);
        abortJobHandler.process(mockAbortRequest);

        mockCancelJob.setStatus(StatusUpdate.STATUS_PENDING);
        abortJobHandler.process(mockAbortRequest);

        mockCancelJob.setStatus(StatusUpdate.STATUS_SUBMITTED);
        abortJobHandler.process(mockAbortRequest);

        mockCancelJob.setStatus(StatusUpdate.STATUS_CANCELLED);
        abortJobHandler.process(mockAbortRequest);
    }

    @Test(expected = PiazzaJobException.class)
    public void testAbortJobMissingJob() throws PiazzaJobException {
        PiazzaJobRequest errorReq = new PiazzaJobRequest();
        errorReq.jobType = new AbortJob("missing_job_id");

        //Test that a non-existent job causes an exception.
        abortJobHandler.process(errorReq);
    }

    @Test(expected = PiazzaJobException.class)
    public void testAbortJobException() throws PiazzaJobException {
        PiazzaJobRequest errorReq = new PiazzaJobRequest();
        errorReq.jobType = new AbortJob("error_job_id");

        //Test that unchecked exceptions are caught and converted to PiazzaJobException.
        Mockito.when(accessor.getJobById("error_job_id")).thenThrow(ResourceAccessException.class);
        abortJobHandler.process(errorReq);
    }

    /**
     * Tests the updating of a Status
     */
    @Test
    public void testUpdateStatus() throws Exception {
        // Mock
        StatusUpdate mockStatus = new StatusUpdate(StatusUpdate.STATUS_RUNNING, new JobProgress(50));
        mockStatus.setResult(new TextResult("Done"));
        mockStatus.setJobId("123456");
        Mockito.doNothing().when(accessor).updateJobStatus(eq("123456"), eq(StatusUpdate.STATUS_RUNNING));
        Mockito.doNothing().when(accessor).updateJobProgress(eq("123456"), any(JobProgress.class));
        when(accessor.getJobById(eq("123456"))).thenReturn(new Job());
        Mockito.doNothing().when(accessor).removeJob(eq("123456"));
        Mockito.doNothing().when(accessor).addJob(any(Job.class));

        // Test
        updateJobHandler.process(mockStatus);

        //Test the exception case for coverage.
        doThrow(RuntimeException.class).when(this.accessor).updateJobStatus(eq("error_job_id"), any(StatusUpdate.class));
        StatusUpdate errorReq = new StatusUpdate();
        errorReq.setJobId("error_job_id");
        updateJobHandler.process(errorReq);
    }

    @Test
    public void testRepeatJob() {
        this.repeatJobHandler.process(mockJob, "my_repeat_job_id");

        Mockito.verify(this.requestJobHandler, times(1)).process(any(), anyString());
    }
}
