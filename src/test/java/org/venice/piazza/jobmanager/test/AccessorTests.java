package org.venice.piazza.jobmanager.test;

import org.venice.piazza.jobmanager.database.DatabaseAccessor;
import model.job.Job;
import model.job.JobProgress;
import model.job.result.ResultType;
import model.response.Pagination;
import model.status.StatusUpdate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.venice.piazza.common.hibernate.dao.job.JobDao;
import org.venice.piazza.common.hibernate.entity.JobEntity;

import java.util.Collections;

import static org.mockito.Mockito.*;

public class AccessorTests {
    @Mock
    private JobDao jobDao;
    @InjectMocks
    private DatabaseAccessor databaseAccessor;


    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetStatusCount() {
        long numberError = 5L;
        long numberSuccess = 10L;

        when(jobDao.countJobByStatus("Error")).thenReturn(numberError);
        when(jobDao.countJobByStatus("Success")).thenReturn(numberSuccess);

        Assert.assertEquals((long) this.databaseAccessor.getJobStatusCount("Error"), numberError);
        Assert.assertEquals((long) this.databaseAccessor.getJobStatusCount("Success"), numberSuccess);
    }

    @Test
    public void testGetJobsCount() {
        long numberJobs = 5L;
        when(this.jobDao.count()).thenReturn(numberJobs);
        Assert.assertEquals(this.databaseAccessor.getJobsCount(), numberJobs);
    }

    @Test
    public void testGetJobyById() {
        String jobIdNull = "null_job_id";
        String jobIdValid = "valid_job_id";

        JobEntity jobEntity = new JobEntity();
        Job job = new Job();
        jobEntity.setJob(job);

        when(this.jobDao.getJobByJobId(jobIdNull)).thenReturn(null);
        when(this.jobDao.getJobByJobId(jobIdValid)).thenReturn(jobEntity);

        Assert.assertEquals(null, this.databaseAccessor.getJobById(jobIdNull));
        Assert.assertEquals(job, this.databaseAccessor.getJobById(jobIdValid));
    }

    @Test
    public void testGetJobs() {
        int page = 5;
        int perPage = 25;
        String order = "desc";
        String sortBy = "id";
        String status = "my_status";
        String userName = "my_user_name";

        Page<JobEntity> entityPage = (Page<JobEntity>) Mockito.mock(Page.class);
        JobEntity entity = Mockito.mock(JobEntity.class);

        Mockito.when(this.jobDao.getJobListForUserAndStatus(anyString(), anyString(), any(Pagination.class))).thenReturn(entityPage);
        Mockito.when(this.jobDao.getJobListByUser(anyString(), any(Pagination.class))).thenReturn(entityPage);
        Mockito.when(this.jobDao.getJobListByStatus(anyString(), any(Pagination.class))).thenReturn(entityPage);
        Mockito.when(this.jobDao.getJobList(any(Pagination.class))).thenReturn(entityPage);

        Mockito.when(entityPage.iterator()).thenReturn(Collections.singleton(entity).iterator());

        Assert.assertNotNull(this.databaseAccessor.getJobs(page, perPage, order, sortBy, status, userName));
        Assert.assertNotNull(this.databaseAccessor.getJobs(page, perPage, order, sortBy, "", userName));
        Assert.assertNotNull(this.databaseAccessor.getJobs(page, perPage, order, sortBy, status, ""));
        Assert.assertNotNull(this.databaseAccessor.getJobs(page, perPage, order, sortBy, "", ""));
    }

    @Test
    public void testUpdateJobStatus() {
        JobEntity entity = new JobEntity();
        Job job = new Job();
        entity.setJob(job);

        Mockito.when(this.jobDao.getJobByJobId("my_job_id")).thenReturn(entity);

        this.databaseAccessor.updateJobStatus("my_job_id", "my_status");
        this.databaseAccessor.updateJobStatus("invalid_job_id", "my_invalid_status");

        Assert.assertEquals("my_status", job.getStatus());
    }

    @Test
    public void testUpdateJobProgress() {
        JobEntity entity = new JobEntity(new Job());
        entity.getJob().setJobId("my_job_id");
        JobProgress progress = Mockito.mock(JobProgress.class);

        Mockito.when(this.jobDao.getJobByJobId("my_job_id")).thenReturn(entity);

        this.databaseAccessor.updateJobProgress("my_job_id", progress);
        this.databaseAccessor.updateJobProgress("my_invalid_id", Mockito.mock(JobProgress.class));

        Assert.assertNotNull(progress);
        Assert.assertEquals(entity.getJob().getProgress(), progress);
    }

    @Test
    public void testUpdateJobStatus2Params()  {
        JobEntity entity = new JobEntity(new Job());
        entity.getJob().setJobId("my_job_id");
        JobProgress progress = Mockito.mock(JobProgress.class);
        ResultType resultType = Mockito.mock(ResultType.class);

        //Mock an empty job status.
        StatusUpdate emptyStatus = Mockito.mock(StatusUpdate.class);
        Mockito.when(emptyStatus.getStatus()).thenReturn("");
        Mockito.when(emptyStatus.getProgress()).thenReturn(null);
        Mockito.when(emptyStatus.getResult()).thenReturn(null);

        //Mock an invalid job status.
        StatusUpdate badStatus = Mockito.mock(StatusUpdate.class);
        Mockito.when(badStatus.getStatus()).thenReturn("my_invalid_status");
        Mockito.when(badStatus.getProgress()).thenReturn(Mockito.mock(JobProgress.class));
        Mockito.when(badStatus.getResult()).thenReturn(Mockito.mock(ResultType.class));

        //Mock a good job status.
        StatusUpdate validStatus = Mockito.mock(StatusUpdate.class);
        Mockito.when(validStatus.getStatus()).thenReturn("my_status");
        Mockito.when(validStatus.getProgress()).thenReturn(progress);
        Mockito.when(validStatus.getResult()).thenReturn(resultType);

        Mockito.when(this.jobDao.getJobByJobId("my_job_id")).thenReturn(entity);

        //Call update with the valid values.
        this.databaseAccessor.updateJobStatus("my_job_id", validStatus);
        //Call it again to make sure we hit the "null" side of all the branches. (code coverage)
        this.databaseAccessor.updateJobStatus("my_job_id", emptyStatus);
        this.databaseAccessor.updateJobStatus("my_invalid_id", badStatus);

        Assert.assertEquals(entity.getJob().getStatus(), validStatus.getStatus());
        Assert.assertEquals(entity.getJob().getProgress(), validStatus.getProgress());
        Assert.assertEquals(entity.getJob().getResult(), validStatus.getResult());
    }

    @Test
    public void testRemoveJob() {
        Mockito.when(this.jobDao.getJobByJobId("my_job_id")).thenReturn(Mockito.mock(JobEntity.class));

        this.databaseAccessor.removeJob("my_job_id");
        Mockito.verify(this.jobDao, times(1)).delete(any(JobEntity.class));

        //Test deleting an invalid job. jobDao.delete should not be called again.
        this.databaseAccessor.removeJob("my_invalid_id");
        Mockito.verify(this.jobDao, times(1)).delete(any(JobEntity.class));
    }

    @Test
    public void testAddJob() {
        this.databaseAccessor.addJob(Mockito.mock(Job.class));
        Mockito.verify(this.jobDao, times(1)).save(any(JobEntity.class));
    }
}
