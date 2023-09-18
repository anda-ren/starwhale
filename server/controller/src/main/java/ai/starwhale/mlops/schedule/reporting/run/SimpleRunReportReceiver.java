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

package ai.starwhale.mlops.schedule.reporting.run;

import ai.starwhale.mlops.domain.job.cache.HotJobHolder;
import ai.starwhale.mlops.domain.run.RunEntity;
import ai.starwhale.mlops.domain.run.RunMapper;
import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.domain.task.status.TaskStatus;
import ai.starwhale.mlops.domain.task.status.TaskStatusMachine;
import ai.starwhale.mlops.schedule.reporting.ReportedTask;
import ai.starwhale.mlops.schedule.reporting.TaskReportReceiver;
import java.util.List;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class SimpleRunReportReceiver implements RunReportReceiver {

    final RunMapper runMapper;

    final TaskStatusMachine taskStatusMachine;

    final HotJobHolder jobHolder;

    final TaskReportReceiver taskReportReceiver;

    public SimpleRunReportReceiver(
            RunMapper runMapper, TaskStatusMachine taskStatusMachine, HotJobHolder jobHolder,
            TaskReportReceiver taskReportReceiver
    ) {
        this.runMapper = runMapper;
        this.taskStatusMachine = taskStatusMachine;
        this.jobHolder = jobHolder;
        this.taskReportReceiver = taskReportReceiver;
    }

    @Override
    public void receive(ReportedRun reportedRun) {
        RunEntity runEntity = runMapper.get(reportedRun.getId());
        Long taskId = runEntity.getTaskId();
        Task task = jobHolder.taskWithId(taskId);
        if (task == null) {
            log.warn("detached task run reported, taskId: {}, runId: {}", taskId, reportedRun.getId());
            return;
        }
        TaskStatus taskNewStatus = taskStatusMachine.transfer(task.getStatus(), reportedRun.getStatus());
        taskReportReceiver.receive(
                List.of(
                        ReportedTask.builder()
                                .id(taskId)
                                .status(taskNewStatus)
                                .ip(reportedRun.getIp())
                                .startTimeMillis(reportedRun.getStartTimeMillis())
                                .stopTimeMillis(reportedRun.getStopTimeMillis())
                                .failedReason(reportedRun.getFailedReason())
                                .generation(reportedRun.getId())
                                .build()
                )
        );

    }
}
