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

package ai.starwhale.mlops.schedule;

import ai.starwhale.mlops.domain.run.RunEntity;
import ai.starwhale.mlops.domain.run.RunMapper;
import ai.starwhale.mlops.domain.run.bo.Run;
import ai.starwhale.mlops.domain.run.bo.RunSpec;
import ai.starwhale.mlops.domain.storage.StoragePathCoordinator;
import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.exception.SwProcessException;
import ai.starwhale.mlops.exception.SwProcessException.ErrorType;
import ai.starwhale.mlops.exception.SwValidationException;
import ai.starwhale.mlops.exception.SwValidationException.ValidSubject;
import ai.starwhale.mlops.schedule.executor.RunExecutor;
import ai.starwhale.mlops.schedule.impl.container.ContainerSpecification;
import ai.starwhale.mlops.schedule.impl.container.TaskContainerSpecificationFinder;
import ai.starwhale.mlops.schedule.reporting.RunReportReceiver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.MessageFormat;
import java.util.UUID;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
public class SwTaskSchedulerImpl implements SwTaskScheduler {

    final RunExecutor runExecutor;
    final RunReportReceiver runReportReceiver;
    final TaskContainerSpecificationFinder taskContainerSpecificationFinder;

    final RunMapper runMapper;

    final ObjectMapper objectMapper;

    final StoragePathCoordinator storagePathCoordinator;

    public SwTaskSchedulerImpl(
            RunExecutor runExecutor,
            RunReportReceiver runReportReceiver,
            TaskContainerSpecificationFinder taskContainerSpecificationFinder,
            RunMapper runMapper,
            ObjectMapper objectMapper,
            StoragePathCoordinator storagePathCoordinator
    ) {
        this.runExecutor = runExecutor;
        this.runReportReceiver = runReportReceiver;
        this.taskContainerSpecificationFinder = taskContainerSpecificationFinder;
        this.runMapper = runMapper;
        this.objectMapper = objectMapper;
        this.storagePathCoordinator = storagePathCoordinator;
    }

    @Override
    @Transactional
    public void schedule(Task task) {
        ContainerSpecification containerSpecification = taskContainerSpecificationFinder.findCs(task);
        RunSpec runSpec = RunSpec.builder()
                .image(containerSpecification.getImage())
                .envs(containerSpecification.getContainerEnvs())
                .command(containerSpecification.getCmd())
                .resourcePool(task.getStep().getResourcePool())
                .requestedResources(task.getTaskRequest().getRuntimeResources())
                .build();
        String runSpecStr = null;
        try {
            runSpecStr = objectMapper.writeValueAsString(runSpec);
        } catch (JsonProcessingException e) {
            throw new SwProcessException(ErrorType.SYSTEM, "failed to serialize runSpec", e);
        }
        RunEntity runEntity = RunEntity.builder()
                .taskId(task.getId())
                .runSpec(runSpecStr)
                .build();
        runMapper.insert(runEntity);
        runEntity.setLogPath(runLogPath(task, runEntity.getId()));
        runMapper.update(runEntity);
        Run run = Run.builder()
                .id(runEntity.getId())
                .taskId(task.getId())
                .runSpec(runSpec)
                .build();
        task.setCurrentRun(run);
        runExecutor.run(run, runReportReceiver);
    }

    @NotNull
    private String runLogPath(Task task, Long runId) {
        return storagePathCoordinator.allocateTaskResultPath(
                task.getStep().getJob().getId().toString(),
                task.getId().toString()
        ) + "/logs/" + runId.toString();
    }

    @Override
    public void stop(Task task) {
        try {
            Run run = task.getCurrentRun();
            if (run != null) {
                runExecutor.stop(run);
            }
        }catch (Throwable e){
            log.error("try to stop task {} failed", task.getId(), e);
        }
    }

    @Override
    public Future<String[]> exec(Task task, String... command) {
        Run currentRun = task.getCurrentRun();
        if(null == currentRun){
            throw new SwValidationException(ValidSubject.TASK, MessageFormat.format("task {0} is not running", task.getId()));
        }
        return runExecutor.exec(currentRun);
    }
}
