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

package ai.starwhale.mlops.schedule.reporting;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ai.starwhale.mlops.domain.job.bo.Job;
import ai.starwhale.mlops.domain.job.bo.JobRuntime;
import ai.starwhale.mlops.domain.job.step.bo.Step;
import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.domain.task.status.TaskStatus;
import ai.starwhale.mlops.domain.task.status.watchers.TaskWatcherForSchedule;
import ai.starwhale.mlops.schedule.SwTaskScheduler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class RunUpdateListenerForGcTest {

    @Test
    public void testDelayStopSchedule() throws InterruptedException {
        SwTaskScheduler swTaskScheduler = mock(
                SwTaskScheduler.class);
        TaskWatcherForSchedule taskWatcherForSchedule = new TaskWatcherForSchedule(swTaskScheduler);
        Task task = Task.builder()
                .id(1L)
                .uuid(UUID.randomUUID().toString())
                .status(TaskStatus.FAIL)
                .step(Step.builder().job(Job.builder().jobRuntime(JobRuntime.builder()
                                                                          .build()).build()).build())
                .build();
        long current = System.currentTimeMillis() - 1000 * 60L + 1000; // +1s prevent immediately deletion

        Instant instant = Instant.ofEpochMilli(current);
        try (MockedStatic<Instant> mockedStatic = mockStatic(Instant.class)) {
            mockedStatic.when(Instant::now).thenReturn(instant);
            taskWatcherForSchedule.onTaskStatusChange(task, TaskStatus.RUNNING);
            task.updateStatus(TaskStatus.SUCCESS);
            taskWatcherForSchedule.onTaskStatusChange(task, TaskStatus.RUNNING);
            taskWatcherForSchedule.processTaskDeletion();
            verify(swTaskScheduler, times(0)).stop(List.of(task));
            Thread.sleep(2000);
            taskWatcherForSchedule.processTaskDeletion();
            verify(swTaskScheduler, times(1)).stop(List.of(task, task));
        }
    }
}
