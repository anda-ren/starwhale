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

package ai.starwhale.mlops.domain.ft;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.starwhale.mlops.api.protocol.ft.FineTuneCreateRequest;
import ai.starwhale.mlops.common.IdConverter;
import ai.starwhale.mlops.configuration.FeaturesProperties;
import ai.starwhale.mlops.domain.dataset.DatasetDao;
import ai.starwhale.mlops.domain.dataset.bo.DatasetVersion;
import ai.starwhale.mlops.domain.ft.mapper.FineTuneMapper;
import ai.starwhale.mlops.domain.ft.mapper.FineTuneSpaceMapper;
import ai.starwhale.mlops.domain.ft.po.FineTuneEntity;
import ai.starwhale.mlops.domain.ft.po.FineTuneSpaceEntity;
import ai.starwhale.mlops.domain.job.JobCreator;
import ai.starwhale.mlops.domain.job.bo.Job;
import ai.starwhale.mlops.domain.job.bo.UserJobCreateRequest;
import ai.starwhale.mlops.domain.job.converter.UserJobConverter;
import ai.starwhale.mlops.domain.job.mapper.JobMapper;
import ai.starwhale.mlops.domain.job.po.JobEntity;
import ai.starwhale.mlops.domain.job.spec.JobSpecParser;
import ai.starwhale.mlops.domain.job.spec.StepSpec;
import ai.starwhale.mlops.domain.model.ModelDao;
import ai.starwhale.mlops.domain.model.po.ModelEntity;
import ai.starwhale.mlops.domain.model.po.ModelVersionEntity;
import ai.starwhale.mlops.domain.project.bo.Project;
import ai.starwhale.mlops.domain.user.bo.User;
import ai.starwhale.mlops.exception.SwNotFoundException;
import ai.starwhale.mlops.exception.SwValidationException;
import ai.starwhale.mlops.exception.api.StarwhaleApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class FineTuneAppServiceTest {

    JobCreator jobCreator;

    FineTuneMapper fineTuneMapper;
    FineTuneSpaceMapper fineTuneSpaceMapper;

    JobMapper jobMapper;

    JobSpecParser jobSpecParser;

    ModelDao modelDao;

    DatasetDao datasetDao;

    FineTuneAppService fineTuneAppService;

    FeaturesProperties featuresProperties;

    User creator = User.builder().build();

    @BeforeEach
    public void setup() {
        jobCreator = mock(JobCreator.class);
        fineTuneMapper = mock(FineTuneMapper.class);
        jobMapper = mock(JobMapper.class);
        jobSpecParser = mock(JobSpecParser.class);
        modelDao = mock(ModelDao.class);
        datasetDao = mock(DatasetDao.class);
        UserJobConverter jobConverter = mock(UserJobConverter.class);
        when(jobConverter.convert(any(), any())).thenReturn(UserJobCreateRequest.builder().build());
        fineTuneSpaceMapper = mock(FineTuneSpaceMapper.class);
        featuresProperties = mock(FeaturesProperties.class);
        when(featuresProperties.isFineTuneEnabled()).thenReturn(true);
        fineTuneAppService = new FineTuneAppService(
                featuresProperties,
                jobCreator,
                fineTuneMapper,
                jobMapper,
                jobSpecParser,
                new IdConverter(),
                modelDao,
                "instanceuri",
                datasetDao,
                fineTuneSpaceMapper, jobConverter//todo
        );
    }

    @Test
    void createFt() throws JsonProcessingException {
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                ((FineTuneEntity) args[0]).setId(123L);
                return null; // void method, so return null
            }
        }).when(fineTuneMapper).add(any());
        when(jobCreator.createJob(any())).thenReturn(Job.builder().id(22L).build());

        FineTuneCreateRequest request = new FineTuneCreateRequest();
        request.setStepSpecOverWrites("aaa");
        request.setEvalDatasetVersionIds(List.of(1L));
        when(datasetDao.getDatasetVersion(anyLong())).thenReturn(DatasetVersion.builder().projectId(22L).datasetName(
                "dsn").versionName("dsv").build());
        when(jobSpecParser.parseAndFlattenStepFromYaml(any())).thenReturn(List.of(StepSpec.builder().build()));
        fineTuneAppService.createFineTune(1L, Project.builder().id(1L).build(), request, User.builder().build());

        verify(fineTuneMapper).updateJobId(123L, 22L);

    }

    @Test
    void listFt() {
        when(fineTuneMapper.list(anyLong())).thenReturn(List.of(FineTuneEntity.builder().jobId(1L).build()));
        when(jobMapper.findJobById(1L)).thenReturn(JobEntity.builder().build());
        Assertions.assertEquals(1, fineTuneAppService.list(1L, 1, 1).getSize());
    }

    @Test
    void evalFt() {
    }

    @Test
    void releaseFt() {
        when(fineTuneMapper.findById(5L)).thenReturn(
                FineTuneEntity.builder()
                        .targetModelVersionId(6L)
                        .spaceId(1L)
                        .build()
        );
        when(modelDao.findByNameForUpdate(any(), anyLong())).thenReturn(ModelEntity.builder().id(124L).build());
        when(modelDao.getModelVersion("6")).thenReturn(ModelVersionEntity
                                                               .builder()
                                                               .modelId(10L)
                                                               .modelName("aac")
                                                               .draft(true)
                                                               .build());
        when(fineTuneSpaceMapper.findById(anyLong())).thenReturn(FineTuneSpaceEntity.builder().projectId(1L).build());
        Assertions.assertThrows(SwValidationException.class, () -> {
            fineTuneAppService.releaseFt(5L, null, "aabc", creator);
        });
    }

    @Test
    void releaseAndCreateNew() {
        when(fineTuneMapper.findById(5L)).thenReturn(
                FineTuneEntity.builder()
                        .targetModelVersionId(6L)
                        .spaceId(1L)
                        .build()
        );
        when(fineTuneSpaceMapper.findById(anyLong())).thenReturn(FineTuneSpaceEntity.builder().projectId(1L).build());
        when(modelDao.getModelVersion("6")).thenReturn(ModelVersionEntity
                                                               .builder()
                                                               .modelId(10L)
                                                               .modelName("aac")
                                                               .draft(true)
                                                               .build());
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                ((ModelEntity) args[0]).setId(123L);
                return null; // void method, so return null
            }
        }).when(modelDao).add(any());
        fineTuneAppService.releaseFt(5L, null, "aab", creator);
        verify(modelDao).releaseModelVersion(6L, 123L);
    }

    @Test
    void releaseSuccessWithBaseModel() {
        when(fineTuneMapper.findById(5L)).thenReturn(
                FineTuneEntity.builder()
                        .targetModelVersionId(6L)
                        .spaceId(1L)
                        .build()
        );
        when(modelDao.getModelVersion("6")).thenReturn(ModelVersionEntity
                                                               .builder()
                                                               .modelId(10L)
                                                               .modelName("aac")
                                                               .draft(true)
                                                               .build());
        fineTuneAppService.releaseFt(5L, 10L, null, creator);
        verify(modelDao).releaseModelVersion(6L, 10L);
    }

    @Test
    void testTargetVersionReleased() {
        when(fineTuneMapper.findById(3L)).thenReturn(
                FineTuneEntity.builder()
                        .targetModelVersionId(4L)
                        .build()
        );
        when(modelDao.getModelVersion("4")).thenReturn(ModelVersionEntity
                                                               .builder()
                                                               .draft(false)
                                                               .build());
        Assertions.assertThrows(SwValidationException.class, () -> {
            fineTuneAppService.releaseFt(3L, 1L, "", null);
        });
    }

    @Test
    void testTargetVersionNull() {
        when(fineTuneMapper.findById(2L)).thenReturn(
                FineTuneEntity.builder()
                        .targetModelVersionId(null)
                        .build()
        );
        Assertions.assertThrows(SwNotFoundException.class, () -> {
            fineTuneAppService.releaseFt(2L, 1L, "", null);
        });
    }

    @Test
    void testReleaseFtNotFound() {
        when(fineTuneMapper.findById(1L)).thenReturn(null);
        Assertions.assertThrows(SwNotFoundException.class, () -> {
            fineTuneAppService.releaseFt(1L, 1L, "", null);
        });
    }

    @Test
    void testFeatureDisabled() {
        when(featuresProperties.isFineTuneEnabled()).thenReturn(false);
        Assertions.assertThrows(StarwhaleApiException.class,
                () -> fineTuneAppService.createFineTune(
                        1L,
                        Project.builder().build(),
                        new FineTuneCreateRequest(),
                        User.builder().build()
                )
        );

        Assertions.assertThrows(StarwhaleApiException.class, () -> fineTuneAppService.list(1L, 1, 1));
    }
}
