/*
 * Copyright 2022 Starwhale, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.starwhale.mlops.domain.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.starwhale.mlops.common.LocalDateTimeConvertor;
import ai.starwhale.mlops.domain.job.bo.Job;
import ai.starwhale.mlops.domain.job.cache.HotJobHolder;
import ai.starwhale.mlops.domain.job.mapper.JobMapper;
import ai.starwhale.mlops.domain.job.status.JobStatus;
import ai.starwhale.mlops.domain.job.status.JobStatusCalculator;
import ai.starwhale.mlops.domain.job.status.JobStatusMachine;
import ai.starwhale.mlops.domain.job.status.JobUpdateHelper;
import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.domain.task.status.TaskStatus;
import ai.starwhale.mlops.domain.task.status.TaskStatusMachine;
import ai.starwhale.mlops.schedule.SWTaskScheduler;
import ai.starwhale.mlops.JobMockHolder;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * test for {@link JobUpdateHelper}
 */
public class JobUpdateHelperTest {

    TaskStatusMachine taskStatusMachine = new TaskStatusMachine();

    @Test
    public void testSuccess(){
        HotJobHolder  hotJobHolder = mock(HotJobHolder.class);
        JobStatusCalculator jobStatusCalculator = mock(JobStatusCalculator.class);

        JobStatusMachine jobStatusMachine = new JobStatusMachine();
        JobMapper jobMapper = mock(JobMapper.class);
        SWTaskScheduler swTaskScheduler = mock(SWTaskScheduler.class);
        LocalDateTimeConvertor localDateTimeConvertor = mock(LocalDateTimeConvertor.class) ;
        LocalDateTime now = LocalDateTime.now();
        when(localDateTimeConvertor.revert(anyLong())).thenReturn(now);

        JobUpdateHelper jobUpdateHelper = new JobUpdateHelper(hotJobHolder,jobStatusCalculator,jobMapper,jobStatusMachine,swTaskScheduler,localDateTimeConvertor,
            taskStatusMachine);
        Job mockJob = new JobMockHolder().mockJob();
        mockJob.getSteps().parallelStream().forEach(step->{
            step.getTasks().parallelStream().forEach(t->{
                t.updateStatus(TaskStatus.SUCCESS);
            });
        });

        JobStatus desiredStatus = JobStatus.SUCCESS;
        when(jobStatusCalculator.desiredJobStatus(anyCollection())).thenReturn(desiredStatus);
        jobUpdateHelper.updateJob(mockJob);
        Assertions.assertEquals(desiredStatus,mockJob.getStatus());
        verify(jobMapper).updateJobStatus(List.of(mockJob.getId()),desiredStatus);
        verify(hotJobHolder).remove(mockJob.getId());
        verify(jobMapper).updateJobFinishedTime(List.of(mockJob.getId()),now);


    }

    @Test
    public void testFAIL() throws InterruptedException {
        HotJobHolder  hotJobHolder = mock(HotJobHolder.class);
        JobStatusCalculator jobStatusCalculator = mock(JobStatusCalculator.class);

        JobStatusMachine jobStatusMachine = new JobStatusMachine();
        JobMapper jobMapper = mock(JobMapper.class);
        SWTaskScheduler swTaskScheduler = mock(SWTaskScheduler.class);
        LocalDateTimeConvertor localDateTimeConvertor = mock(LocalDateTimeConvertor.class) ;
        LocalDateTime now = LocalDateTime.now();
        when(localDateTimeConvertor.revert(anyLong())).thenReturn(now);

        JobUpdateHelper jobUpdateHelper = new JobUpdateHelper(hotJobHolder,jobStatusCalculator,jobMapper,jobStatusMachine,swTaskScheduler,localDateTimeConvertor,
            taskStatusMachine);
        Job mockJob = new JobMockHolder().mockJob();


        Task luckTask = mockJob.getSteps().get(0).getTasks().get(0);
        luckTask.updateStatus(TaskStatus.RUNNING);
        JobStatus desiredStatus = JobStatus.FAIL;
        when(jobStatusCalculator.desiredJobStatus(anyCollection())).thenReturn(desiredStatus);
        jobUpdateHelper.updateJob(mockJob);
        Assertions.assertEquals(desiredStatus,mockJob.getStatus());
        verify(jobMapper,times(1)).updateJobStatus(List.of(mockJob.getId()),desiredStatus);
        verify(hotJobHolder).remove(mockJob.getId());
        verify(jobMapper).updateJobFinishedTime(List.of(mockJob.getId()),now);
        Thread.sleep(100);//wait for async status update
        Assertions.assertEquals(TaskStatus.CANCELED,luckTask.getStatus());

    }

    @Test
    public void testCanceled(){

        HotJobHolder  hotJobHolder = mock(HotJobHolder.class);
        JobStatusCalculator jobStatusCalculator = mock(JobStatusCalculator.class);

        JobStatusMachine jobStatusMachine = new JobStatusMachine();
        JobMapper jobMapper = mock(JobMapper.class);
        SWTaskScheduler swTaskScheduler = mock(SWTaskScheduler.class);
        LocalDateTimeConvertor localDateTimeConvertor = mock(LocalDateTimeConvertor.class) ;
        LocalDateTime now = LocalDateTime.now();
        when(localDateTimeConvertor.revert(anyLong())).thenReturn(now);

        JobUpdateHelper jobUpdateHelper = new JobUpdateHelper(hotJobHolder,jobStatusCalculator,jobMapper,jobStatusMachine,swTaskScheduler,localDateTimeConvertor,
            taskStatusMachine);
        Job mockJob = new JobMockHolder().mockJob();

        mockJob.setStatus(JobStatus.RUNNING);
        JobStatus desiredStatus = JobStatus.CANCELED;
        when(jobStatusCalculator.desiredJobStatus(anyCollection())).thenReturn(desiredStatus);
        jobUpdateHelper.updateJob(mockJob);
        Assertions.assertEquals(desiredStatus,mockJob.getStatus());
        verify(jobMapper).updateJobStatus(List.of(mockJob.getId()),desiredStatus);
        verify(jobMapper).updateJobFinishedTime(List.of(mockJob.getId()),now);

    }

    @Test
    public void testRunning(){
        HotJobHolder  hotJobHolder = mock(HotJobHolder.class);
        JobStatusCalculator jobStatusCalculator = mock(JobStatusCalculator.class);

        JobStatusMachine jobStatusMachine = new JobStatusMachine();
        JobMapper jobMapper = mock(JobMapper.class);
        SWTaskScheduler swTaskScheduler = mock(SWTaskScheduler.class);
        LocalDateTimeConvertor localDateTimeConvertor = mock(LocalDateTimeConvertor.class) ;

        JobUpdateHelper jobUpdateHelper = new JobUpdateHelper(hotJobHolder,jobStatusCalculator,jobMapper,jobStatusMachine,swTaskScheduler,localDateTimeConvertor,
            taskStatusMachine);
        Job mockJob = new JobMockHolder().mockJob();

        mockJob.setStatus(JobStatus.READY);
        JobStatus desiredStatus = JobStatus.RUNNING;
        when(jobStatusCalculator.desiredJobStatus(anyCollection())).thenReturn(desiredStatus);
        jobUpdateHelper.updateJob(mockJob);
        Assertions.assertEquals(JobStatus.RUNNING,mockJob.getStatus());
        verify(jobMapper,times(1)).updateJobStatus(List.of(mockJob.getId()),desiredStatus);
    }
}