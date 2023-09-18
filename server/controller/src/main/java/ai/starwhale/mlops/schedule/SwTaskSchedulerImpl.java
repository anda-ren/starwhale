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
import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.schedule.executor.RunExecutor;
import ai.starwhale.mlops.schedule.impl.container.ContainerSpecification;
import ai.starwhale.mlops.schedule.impl.container.TaskContainerSpecificationFinder;
import ai.starwhale.mlops.schedule.reporting.TaskReportReceiver;
import ai.starwhale.mlops.schedule.reporting.run.RunReportReceiver;
import java.util.Collection;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;


@Slf4j
@Service
public class SwTaskSchedulerImpl implements SwTaskScheduler {

    final RunExecutor runExecutor;
    final RunReportReceiver runReportReceiver;
    final TaskContainerSpecificationFinder taskContainerSpecificationFinder;

    final RunMapper runMapper;

    public SwTaskSchedulerImpl(
            RunExecutor runExecutor,
            RunReportReceiver runReportReceiver,
            TaskContainerSpecificationFinder taskContainerSpecificationFinder,
            RunMapper runMapper
    ) {
        this.runExecutor = runExecutor;
        this.runReportReceiver = runReportReceiver;
        this.taskContainerSpecificationFinder = taskContainerSpecificationFinder;
        this.runMapper = runMapper;
    }

    @Override
    public void schedule(Collection<Task> tasks, TaskReportReceiver taskReportReceiver) {
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }
        tasks.forEach(task -> {
            ContainerSpecification containerSpecification = taskContainerSpecificationFinder.findCs(task);
            Run run = Run.builder()
                    .id(1L)
                    .taskId(task.getId())
                    .runSpec(
                            RunSpec.builder()
                                    .image(containerSpecification.getImage())
                                    .envs(containerSpecification.getContainerEnvs())
                                    .command(containerSpecification.getCmd())
                                    .resourcePool(task.getStep().getResourcePool())
                                    .requestedResources(task.getTaskRequest().getRuntimeResources())
                                    .build()
                    )
                    .build();
            runMapper.insert(new RunEntity());
            runExecutor.run(run, runReportReceiver);
            task.setCurrentRun(run);
        });

    }

    @Override
    public void stop(Collection<Task> tasks) {
        tasks.forEach(task -> {
            try {
                Run run = task.getCurrentRun();
                if (run != null) {
                    runExecutor.stop(run);
                }
            }catch (Throwable e){
                log.error("try to stop task {} failed", task.getId(), e);
            }

        });
    }

    @Override
    public Future<String[]> exec(Task task, String... command) {
        return runExecutor.exec(task.getCurrentRun());
    }
}
