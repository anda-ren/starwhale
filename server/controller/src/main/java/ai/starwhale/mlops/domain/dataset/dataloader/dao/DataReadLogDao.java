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

package ai.starwhale.mlops.domain.dataset.dataloader.dao;

import ai.starwhale.mlops.domain.dataset.dataloader.Status;
import ai.starwhale.mlops.domain.dataset.dataloader.bo.DataReadLog;
import ai.starwhale.mlops.domain.dataset.dataloader.converter.DataReadLogConverter;
import ai.starwhale.mlops.domain.dataset.dataloader.mapper.DataReadLogMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DataReadLogDao {
    private final DataReadLogMapper mapper;
    private final DataReadLogConverter converter;

    public DataReadLogDao(DataReadLogMapper mapper, DataReadLogConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    public boolean batchInsert(List<DataReadLog> dataReadLogs) {
        var entities = dataReadLogs.stream()
                .map(converter::convert)
                .collect(Collectors.toList());
        return mapper.batchInsert(entities) > 0;
    }


    public boolean updateToAssigned(DataReadLog dataReadLog) {
        return mapper.updateToAssigned(converter.convert(dataReadLog)) > 0;
    }

    public boolean updateToProcessed(Long sid, String consumerId, String start, String end) {
        return mapper.updateToProcessed(sid, consumerId, start, end, Status.DataStatus.PROCESSED.name()) > 0;
    }

    public boolean updateUnProcessedToUnAssigned(String consumerId) {
        return mapper.updateToUnAssignedForConsumer(consumerId, Status.DataStatus.UNPROCESSED.name()) > 0;
    }

    public List<DataReadLog> selectTopsUnAssignedData(Long sid, Integer limit) {
        var entities = mapper.selectTopsUnAssigned(sid, Status.DataStatus.UNPROCESSED.name(), limit);
        return entities.stream().map(converter::revert).collect(Collectors.toList());
    }
}
