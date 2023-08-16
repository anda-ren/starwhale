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

package ai.starwhale.mlops.schedule.entrypoint;


import ai.starwhale.mlops.domain.task.bo.Task;
import ai.starwhale.mlops.exception.SwNotFoundException;
import ai.starwhale.mlops.exception.SwNotFoundException.ResourceType;
import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;


@Service
public class TaskContainerEntrypointBuilderFinder {

    final List<TaskContainerEntrypointBuilder> taskContainerEntrypointBuilders;

    public TaskContainerEntrypointBuilderFinder(List<TaskContainerEntrypointBuilder> taskContainerEntrypointBuilders) {
        this.taskContainerEntrypointBuilders = taskContainerEntrypointBuilders.stream().sorted((b1, b2) -> {
            Order o1 = b1.getClass().getAnnotation(Order.class);
            if (null == o1) {
                return -1;
            }
            Order o2 = b2.getClass().getAnnotation(Order.class);
            if (null == o2) {
                return 1;
            }
            return o1.value() - o2.value();
        }).collect(Collectors.toList());
    }

    public TaskContainerEntrypointBuilder findTceb(Task task){
        for(var tceb : taskContainerEntrypointBuilders){
            if(tceb.matches(task)){
                return  tceb;
            }
        }
        throw new SwNotFoundException(ResourceType.BUNDLE, MessageFormat.format("no matching env builder found for task {0}", task.getId()));
    }

}
