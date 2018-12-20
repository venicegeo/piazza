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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import exception.PiazzaJobException;
import org.venice.piazza.jobmanager.controller.JobManagerController;
import org.venice.piazza.jobmanager.database.JobManagerDatabaseAccessor;
import org.venice.piazza.jobmanager.messaging.handler.AbortJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RepeatJobHandler;
import org.venice.piazza.jobmanager.messaging.handler.RequestJobHandler;
import model.job.Job;
import model.job.JobProgress;
import model.job.type.AbortJob;
import model.job.type.IngestJob;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.JobListResponse;
import model.response.JobResponse;
import model.response.JobStatusResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Job Controller REST Endpoint, which returns Job Status and other Job Table information.
 *
 * @author Patrick.Doody
 */
public class JobControllerTests {
    @Mock
    private PiazzaLogger logger;
    @Mock
    private UUIDFactory uuidFactory;
    @Mock
    private JobManagerDatabaseAccessor accessor;
    @Mock
    private AbortJobHandler abortJobHandler;
    @Mock
    private RepeatJobHandler repeatJobHandler;
    @Mock
    private RequestJobHandler requestJobHandler;
    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @InjectMocks
    private JobManagerController jobController;

    private Job mockJob;
    private List<Job> mockJobs;

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
        mockJob.setCreatedOnString(new DateTime().toString());
        // Mock a list of Jobs
        mockJobs = new ArrayList<Job>();
        mockJobs.add(mockJob);
    }

    /**
     * Test /job/{jobId}
     */
    @Test
    public void testStatus() throws Exception {
        // Test error handling on Null Job Id
        PiazzaResponse response = jobController.getJobStatus(null).getBody();
        assertTrue(response instanceof ErrorResponse);

        //Test error handling on empty Job Id
        PiazzaResponse emptyResponse = jobController.getJobStatus("").getBody();
        assertTrue(emptyResponse instanceof ErrorResponse);

        // When we query the Status of the Mock Job's Id, return the Mock Job
        when(accessor.getJobById(mockJob.getJobId())).thenReturn(mockJob);

        // Query the Job
        response = jobController.getJobStatus(mockJob.getJobId()).getBody();
        assertTrue(response instanceof JobStatusResponse);
        JobStatusResponse jobStatus = (JobStatusResponse) response;
        assertTrue(jobStatus.data.jobId.equals(mockJob.getJobId()));
        assertTrue(jobStatus.data.progress.equals(mockJob.getProgress()));
        assertTrue(jobStatus.data.status.equals(StatusUpdate.STATUS_RUNNING));
        assertTrue(jobStatus.data.jobType.equals(mockJob.getJobType().getClass().getSimpleName()));

        // Test Job Not Exists
        when(accessor.getJobById(mockJob.getJobId())).thenReturn(null);
        response = jobController.getJobStatus(mockJob.getJobId()).getBody();
        assertTrue(response instanceof ErrorResponse);
    }

    /**
     * Test /abort
     */
    @Test
    public void testAbortJob() throws Exception {
        // Mock
        Job job = new Job(); //Mockito.mock(Job.class);
        job.setJobId("123456");
        job.setStatus(StatusUpdate.STATUS_RUNNING);

        when(accessor.getJobById(eq("123456"))).thenReturn(job);
        Mockito.doNothing().when(abortJobHandler).process(any(PiazzaJobRequest.class));
        PiazzaJobRequest mockRequest = new PiazzaJobRequest();
        mockRequest.jobType = new AbortJob("123456");

        // Test
        ResponseEntity<PiazzaResponse> response = jobController.abortJob(mockRequest);
        assertTrue(response.getStatusCode().compareTo(HttpStatus.OK) == 0);

        job.setStatus(StatusUpdate.STATUS_PENDING);
        response = jobController.abortJob(mockRequest);
        assertTrue(response.getStatusCode().compareTo(HttpStatus.OK) == 0);

        job.setStatus(StatusUpdate.STATUS_SUBMITTED);
        response = jobController.abortJob(mockRequest);
        assertTrue(response.getStatusCode().compareTo(HttpStatus.OK) == 0);

        job.setStatus(StatusUpdate.STATUS_CANCELLED);
        response = jobController.abortJob(mockRequest);
        assertTrue(response.getStatusCode().compareTo(HttpStatus.BAD_REQUEST) == 0);

        mockRequest.jobType = new AbortJob("invalid_job_id");
        response = jobController.abortJob(mockRequest);
        assertTrue(response.getStatusCode().compareTo(HttpStatus.NOT_FOUND) == 0);

        // Test Exception
        mockRequest.jobType = new AbortJob("123456");
        job.setStatus(StatusUpdate.STATUS_RUNNING);
        Mockito.doThrow(new PiazzaJobException("Couldn't Abort")).when(abortJobHandler).process(any(PiazzaJobRequest.class));
        response = jobController.abortJob(mockRequest);
        assertTrue(response.getBody() instanceof ErrorResponse);
        assertTrue(response.getStatusCode().compareTo(HttpStatus.INTERNAL_SERVER_ERROR) == 0);
        //assertTrue(((ErrorResponse) response.getBody()).message.contains("Could not Abort Job"));
    }

    /**
     * Test /repeat
     */
    @Test
    public void testRepeat() {
        // Mock
        when(accessor.getJobById(eq("123456"))).thenReturn(mockJob);
        when(uuidFactory.getUUID()).thenReturn("123456");
        Mockito.doNothing().when(repeatJobHandler).process(any(Job.class), any(String.class));

        PiazzaJobRequest mockRequest = new PiazzaJobRequest();
        mockRequest.jobType = new RepeatJob("123456");

        // Test
        PiazzaResponse response = jobController.repeatJob(mockRequest).getBody();

        // Verify
        assertTrue(response instanceof JobResponse);
        assertTrue(((JobResponse) response).data.getJobId().equals("123456"));

        //Test Exception
        when(accessor.getJobById("123456")).thenThrow(RuntimeException.class);
        response = jobController.repeatJob(mockRequest).getBody();
        assertTrue(response instanceof ErrorResponse);
    }

    /**
     * Test /job
     */
    @Test
    public void testGetJobs() {
        JobListResponse mockResponse = new JobListResponse(mockJobs, new Pagination());
        // Mock
        when(accessor.getJobs(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockResponse);

        // Test
        JobListResponse jobs = jobController.getJobs("0", "10", "asc", "jobId", null, null);
        // Verify
        assertTrue(jobs != null);
        assertTrue(jobs.getData().size() == 1);
        assertTrue(jobs.getData().get(0).getJobId().equals(mockJob.getJobId()));
    }

    /**
     * Tests /job/status
     */
    @Test
    public void testStatuses() {
        // Test
        List<String> statuses = jobController.getStatuses();

        // Verify
        assertTrue(statuses.size() == 8);
        assertTrue(statuses.contains(StatusUpdate.STATUS_CANCELLED));
        assertTrue(statuses.contains(StatusUpdate.STATUS_ERROR));
        assertTrue(statuses.contains(StatusUpdate.STATUS_FAIL));
        assertTrue(statuses.contains(StatusUpdate.STATUS_PENDING));
        assertTrue(statuses.contains(StatusUpdate.STATUS_RUNNING));
        assertTrue(statuses.contains(StatusUpdate.STATUS_SUBMITTED));
        assertTrue(statuses.contains(StatusUpdate.STATUS_SUCCESS));
        assertTrue(statuses.contains(StatusUpdate.STATUS_CANCELLING));
    }

    /**
     * Test /admin/stats
     */
    @Test
    public void testAdminStats() {
        // Mock
        when(accessor.getJobsCount()).thenReturn(new Long(10));
        when(accessor.getJobStatusCount(anyString())).thenReturn(new Long(10));

        // Test
        ResponseEntity<Map<String, Object>> entity = jobController.getAdminStats();

        // Verify
        assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
        Map<String, Object> stats = entity.getBody();
        assertTrue(stats.keySet().size() >= 8);
    }

    /**
     * Tests the endpoint to request a job
     */
    @Test
    public void testRequestJob() throws Exception {
        // Mock
        when(uuidFactory.getUUID()).thenReturn("123456");
        PiazzaJobRequest mockRequest = new PiazzaJobRequest();
        mockRequest.jobType = new AbortJob("123456");

        // Test
        ResponseEntity<PiazzaResponse> response = jobController.requestJob(mockRequest, "");
        assertTrue(response.getBody() instanceof JobResponse);
        assertTrue(((JobResponse) response.getBody()).data.getJobId().equals("123456"));

        response = jobController.requestJob(mockRequest, "123456");
        assertTrue(response.getBody() instanceof JobResponse);
        assertTrue(((JobResponse) response.getBody()).data.getJobId().equals("123456"));

        //Test an Error
        Mockito.doThrow(new RuntimeException("Error")).when(requestJobHandler).process(Mockito.any(PiazzaJobRequest.class), Mockito.anyString());
        response = jobController.requestJob(mockRequest, "");
        assertTrue(response.getBody() instanceof ErrorResponse);
    }
}
