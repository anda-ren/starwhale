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

package ai.starwhale.mlops.domain.swds.converter;

import ai.starwhale.mlops.domain.swds.bo.SwDataSet;
import ai.starwhale.mlops.domain.swds.objectstore.StorageAuths;
import ai.starwhale.mlops.domain.swds.po.SwDatasetVersionEntity;
import ai.starwhale.mlops.storage.configuration.StorageProperties;
import ai.starwhale.mlops.storage.fs.FileStorageEnv;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SwdsBoConverter {

    final StorageProperties storageProperties;

    public SwdsBoConverter(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public SwDataSet fromEntity(SwDatasetVersionEntity swDatasetVersionEntity) {
        Map<String, FileStorageEnv> fileStorageEnvs;
        if (StringUtils.hasText(swDatasetVersionEntity.getStorageAuths())) {
            StorageAuths storageAuths = new StorageAuths(swDatasetVersionEntity.getStorageAuths());
            fileStorageEnvs = storageAuths.allEnvs();
        } else {
            fileStorageEnvs = storageProperties.toFileStorageEnvs();
        }
        fileStorageEnvs.values().forEach(fileStorageEnv -> fileStorageEnv.add(FileStorageEnv.ENV_KEY_PREFIX,
                swDatasetVersionEntity.getStoragePath()));
        return SwDataSet.builder()
                .id(swDatasetVersionEntity.getId())
                .name(swDatasetVersionEntity.getDatasetName())
                .version(swDatasetVersionEntity.getVersionName())
                .size(swDatasetVersionEntity.getSize())
                .path(swDatasetVersionEntity.getStoragePath())
                .fileStorageEnvs(fileStorageEnvs)
                .indexTable(swDatasetVersionEntity.getIndexTable())
                .build();
    }
}