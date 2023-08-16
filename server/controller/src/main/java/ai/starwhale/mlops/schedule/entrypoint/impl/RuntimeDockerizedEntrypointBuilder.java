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

import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.schedule.entrypoint.TaskCommand;
import ai.starwhale.mlops.schedule.entrypoint.TaskContainerEntrypointBuilder;
import java.util.Map;
import org.springframework.core.annotation.Order;


@Order(3)
public class RuntimeDockerizedEntrypointBuilder implements TaskContainerEntrypointBuilder {
    public static final String NAME = "runtime_dockerized";

    @Override
    public Map<String, String> buildContainerEnvs(Task task) {
        return null;
    }

    @Override
    public TaskCommand getCmd(Task task) {
        return null;
    }

    @Override
    public String getImage(Task task) {
        return null;
    }

    @Override
    public boolean matches(Task task) {
        return NAME.equals(task.getStep().getJob().getVirtualJobName());
    }
}
