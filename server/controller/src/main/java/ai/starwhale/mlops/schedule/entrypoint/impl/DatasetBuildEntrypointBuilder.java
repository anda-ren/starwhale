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

package ai.starwhale.mlops.schedule.entrypoint.impl;

import ai.starwhale.mlops.common.DockerImage;
import ai.starwhale.mlops.configuration.RunTimeProperties.RunConfig;
import ai.starwhale.mlops.configuration.security.TaskTokenValidator;
import ai.starwhale.mlops.domain.job.bo.Job;
import ai.starwhale.mlops.domain.system.SystemSettingService;
import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.exception.SwValidationException;
import ai.starwhale.mlops.exception.SwValidationException.ValidSubject;
import ai.starwhale.mlops.schedule.entrypoint.TaskCommand;
import ai.starwhale.mlops.schedule.entrypoint.TaskContainerEntrypointBuilder;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Order(1)
@Component
public class DatasetBuildEntrypointBuilder implements TaskContainerEntrypointBuilder {

    final SystemSettingService systemSettingService;
    final TaskTokenValidator taskTokenValidator;

    final String instanceUri;

    public static final String NAME = "dataset_build";

    public DatasetBuildEntrypointBuilder(SystemSettingService systemSettingService,
            @Value("${sw.instance-uri}") String instanceUri,
            TaskTokenValidator taskTokenValidator) {
        this.systemSettingService = systemSettingService;
        this.taskTokenValidator = taskTokenValidator;
        this.instanceUri = instanceUri;
    }

    @Override
    public Map<String, String> buildContainerEnvs(Task task) {
        var runConfig = systemSettingService.getRunTimeProperties();
        if(null == runConfig || null == runConfig.getDatasetBuild()){
            throw new SwValidationException(ValidSubject.SETTING,"dataset builder config which is needed is not set in system setting");
        }
        RunConfig dsBuildConfig = runConfig.getDatasetBuild();
        String swVersion = "";
        String pyVersion = "";
        if (runConfig != null && dsBuildConfig != null) {
            if (StringUtils.hasText(dsBuildConfig.getClientVersion())) {
                swVersion = dsBuildConfig.getClientVersion();
            }
            if (StringUtils.hasText(dsBuildConfig.getPythonVersion())) {
                pyVersion = dsBuildConfig.getPythonVersion();
            }
        }
        Job swJob = task.getStep().getJob();
        var taskEnv = task.getTaskRequest().getEnv();
        Map<String, String> coreContainerEnvs = new HashMap<>();
        if (!CollectionUtils.isEmpty(taskEnv)) {
            taskEnv.forEach(env -> coreContainerEnvs.put(env.getName(), env.getValue()));
        }
        coreContainerEnvs.put("SW_VERSION", swVersion);
        coreContainerEnvs.put("SW_RUNTIME_PYTHON_VERSION", pyVersion);
        coreContainerEnvs.put("SW_PYPI_INDEX_URL", runConfig.getPypi().getIndexUrl());
        coreContainerEnvs.put("SW_PYPI_EXTRA_INDEX_URL", runConfig.getPypi().getExtraIndexUrl());
        coreContainerEnvs.put("SW_PYPI_TRUSTED_HOST", runConfig.getPypi().getTrustedHost());
        coreContainerEnvs.put("SW_PYPI_TIMEOUT", String.valueOf(runConfig.getPypi().getTimeout()));
        coreContainerEnvs.put("SW_PYPI_RETRIES", String.valueOf(runConfig.getPypi().getRetries()));
        coreContainerEnvs.put("SW_INSTANCE_URI", instanceUri);
        coreContainerEnvs.put("SW_PROJECT", swJob.getProject().getName());
        coreContainerEnvs.put("SW_TOKEN", taskTokenValidator.getTaskToken(swJob.getOwner(), task.getId()));
        return coreContainerEnvs;
    }

    @Override
    public TaskCommand getCmd(Task task) {
        return TaskCommand.builder().cmd(new String[]{"dataset_build"}).build();
    }

    @Override
    public String getImage(Task task) {
        var runConfig = systemSettingService.getRunTimeProperties();
        if (!StringUtils.hasText(runConfig.getDatasetBuild().getImage())) {
            throw new SwValidationException(ValidSubject.SETTING,"dataset builder image which is needed is not set in system setting");
        }
        var image = new DockerImage(runConfig.getDatasetBuild().getImage());
        return image.resolve(systemSettingService.getDockerSetting().getRegistryForPull());
    }

    @Override
    public boolean matches(Task task) {
        return NAME.equals(task.getStep().getJob().getVirtualJobName());
    }
}
