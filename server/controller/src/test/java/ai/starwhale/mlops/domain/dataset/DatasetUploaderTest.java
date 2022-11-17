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

package ai.starwhale.mlops.domain.dataset;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.starwhale.mlops.JobMockHolder;
import ai.starwhale.mlops.api.protocol.dataset.upload.UploadRequest;
import ai.starwhale.mlops.common.IdConvertor;
import ai.starwhale.mlops.common.VersionAliasConvertor;
import ai.starwhale.mlops.configuration.json.ObjectMapperConfig;
import ai.starwhale.mlops.domain.dataset.index.datastore.DataStoreTableNameHelper;
import ai.starwhale.mlops.domain.dataset.index.datastore.IndexWriter;
import ai.starwhale.mlops.domain.dataset.mapper.DatasetMapper;
import ai.starwhale.mlops.domain.dataset.mapper.DatasetVersionMapper;
import ai.starwhale.mlops.domain.dataset.po.DatasetEntity;
import ai.starwhale.mlops.domain.dataset.po.DatasetVersionEntity;
import ai.starwhale.mlops.domain.dataset.upload.DatasetUploader;
import ai.starwhale.mlops.domain.dataset.upload.DatasetVersionWithMetaConverter;
import ai.starwhale.mlops.domain.dataset.upload.HotDatasetHolder;
import ai.starwhale.mlops.domain.job.bo.Job;
import ai.starwhale.mlops.domain.job.cache.HotJobHolder;
import ai.starwhale.mlops.domain.job.cache.HotJobHolderImpl;
import ai.starwhale.mlops.domain.project.ProjectManager;
import ai.starwhale.mlops.domain.project.mapper.ProjectMapper;
import ai.starwhale.mlops.domain.project.po.ProjectEntity;
import ai.starwhale.mlops.domain.storage.StoragePathCoordinator;
import ai.starwhale.mlops.domain.user.UserService;
import ai.starwhale.mlops.domain.user.bo.User;
import ai.starwhale.mlops.exception.SwValidationException;
import ai.starwhale.mlops.storage.LengthAbleInputStream;
import ai.starwhale.mlops.storage.StorageAccessService;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * test for {@link DatasetUploader}
 */
public class DatasetUploaderTest {

    final DataStoreTableNameHelper dataStoreTableNameHelper = new DataStoreTableNameHelper();

    final IndexWriter indexWriter = mock(IndexWriter.class);

    @Test
    public void testDatasetUploader() throws IOException {
        YAMLMapper yamlMapper = new ObjectMapperConfig().yamlMapper();
        DatasetVersionWithMetaConverter datasetVersionWithMetaConverter = new DatasetVersionWithMetaConverter(
                yamlMapper);
        HotDatasetHolder hotDatasetHolder = new HotDatasetHolder(datasetVersionWithMetaConverter);
        DatasetMapper datasetMapper = mock(DatasetMapper.class);
        DatasetVersionMapper datasetVersionMapper = mock(DatasetVersionMapper.class);
        StoragePathCoordinator storagePathCoordinator = new StoragePathCoordinator("/test");
        StorageAccessService storageAccessService = mock(StorageAccessService.class);
        UserService userService = mock(UserService.class);
        when(userService.currentUserDetail()).thenReturn(User.builder().idTableKey(1L).build());
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectManager projectManager = mock(ProjectManager.class);
        when(projectManager.findByNameOrDefault(anyString(), anyLong())).thenReturn(
                ProjectEntity.builder().id(1L).build());
        when(projectManager.getProject(anyString())).thenReturn(ProjectEntity.builder().id(1L).build());

        HotJobHolder hotJobHolder = new HotJobHolderImpl();

        DatasetManager datasetManager = mock(DatasetManager.class);
        IdConvertor idConvertor = new IdConvertor();
        VersionAliasConvertor versionAliasConvertor = new VersionAliasConvertor();

        DatasetUploader datasetUploader = new DatasetUploader(hotDatasetHolder, datasetMapper, datasetVersionMapper,
                storagePathCoordinator, storageAccessService, userService, yamlMapper,
                hotJobHolder, projectManager, dataStoreTableNameHelper, indexWriter, datasetManager, idConvertor,
                versionAliasConvertor);

        datasetUploader.create(HotDatasetHolderTest.MANIFEST, "_manifest.yaml", new UploadRequest());
        String dsVersionId = "mizwkzrqgqzdemjwmrtdmmjummzxczi3";
        datasetUploader.uploadBody(
                dsVersionId,
                new MockMultipartFile("index.jsonl", "index.jsonl", "plain/text", index_file_content.getBytes()),
                "abc/index.jsonl");

        datasetUploader.end(dsVersionId);

        verify(storageAccessService).put(anyString(), any(byte[].class));
        verify(storageAccessService).put(anyString(), any(InputStream.class), anyLong());
        verify(datasetVersionMapper).updateStatus(null, DatasetVersionEntity.STATUS_AVAILABLE);
        verify(datasetVersionMapper).addNewVersion(any(DatasetVersionEntity.class));
        String dsName = "testds3";
        verify(datasetMapper).findByName(eq(dsName), anyLong());
        verify(datasetMapper).addDataset(any(DatasetEntity.class));

        when(storageAccessService.list(anyString())).thenReturn(Stream.of("a", "b"));
        datasetUploader.create(HotDatasetHolderTest.MANIFEST, "_manifest.yaml", new UploadRequest());
        datasetUploader.cancel(dsVersionId);
        verify(datasetVersionMapper).deleteById(null);
        verify(storageAccessService).list(anyString());
        verify(storageAccessService).delete("a");
        verify(storageAccessService).delete("b");
        verify(storageAccessService, times(0)).delete("c");

        when(datasetMapper.findByName(eq(dsName), anyLong())).thenReturn(
                DatasetEntity.builder().id(1L).projectId(1L).build());
        DatasetVersionEntity mockedEntity = DatasetVersionEntity.builder()
                .id(1L)
                .versionName("testversion")
                .status(DatasetVersionEntity.STATUS_AVAILABLE)
                .build();
        when(datasetVersionMapper.findByDsIdAndVersionNameForUpdate(1L, dsVersionId)).thenReturn(mockedEntity);
        when(datasetVersionMapper.findByDsIdAndVersionName(1L, dsVersionId)).thenReturn(mockedEntity);
        when(datasetVersionMapper.getVersionById(1L)).thenReturn(mockedEntity);
        when(storageAccessService.get(anyString())).thenReturn(
                new LengthAbleInputStream(
                        new ByteArrayInputStream(index_file_content.getBytes()),
                        index_file_content.getBytes().length
                )
        );
        when(datasetManager.findByName(anyString(), anyLong()))
                .thenReturn(DatasetEntity.builder().id(1L).build());
        when(datasetManager.findVersionByNameAndBundleId(dsVersionId, 1L))
                .thenReturn(mockedEntity);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        ServletOutputStream mockOutPutStream = new ServletOutputStream() {

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {

            }

            @Override
            public void write(int b) throws IOException {

            }

        };
        when(httpResponse.getOutputStream()).thenReturn(mockOutPutStream);
        datasetUploader.pull("project", dsName, dsVersionId, "index.jsonl", httpResponse);

        Assertions.assertThrowsExactly(SwValidationException.class,
                () -> datasetUploader.create(HotDatasetHolderTest.MANIFEST, "_manifest.yaml", new UploadRequest()));

        JobMockHolder jobMockHolder = new JobMockHolder();
        Job mockJob = jobMockHolder.mockJob();
        hotJobHolder.adopt(mockJob);
        UploadRequest uploadRequest = new UploadRequest();
        uploadRequest.setForce("1");
        Assertions.assertThrowsExactly(SwValidationException.class,
                () -> datasetUploader.create(HotDatasetHolderTest.MANIFEST, "_manifest.yaml", uploadRequest));
        hotJobHolder.remove(mockJob.getId());
        datasetUploader.create(HotDatasetHolderTest.MANIFEST, "_manifest.yaml", uploadRequest);
        verify(datasetVersionMapper, times(1)).updateStatus(1L, DatasetVersionEntity.STATUS_UN_AVAILABLE);


    }

    static final String index_file_content = "/*\n"
            + " * Copyright 2022 Starwhale, Inc. All Rights Reserved.\n"
            + " *\n"
            + " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
            + " * you may not use this file except in compliance with the License.\n"
            + " * You may obtain a copy of the License at\n"
            + " *\n"
            + " * http://www.apache.org/licenses/LICENSE-2.0\n"
            + " *\n"
            + " * Unless required by applicable law or agreed to in writing, software\n"
            + " * distributed under the License is distributed on an \"AS IS\" BASIS,\n"
            + " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
            + " * See the License for the specific language governing permissions and\n"
            + " * limitations under the License.\n"
            + " */";
}

