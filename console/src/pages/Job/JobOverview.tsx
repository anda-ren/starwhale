import React from 'react'
import useTranslation from '@/hooks/useTranslation'
import { durationToStr, formatTimestampDateTime } from '@/utils/datetime'
import { useProject } from '@project/hooks/useProject'
import { useJob } from '@/domain/job/hooks/useJob'
import JobStatus from '@/domain/job/components/JobStatus'
import Alias from '@/components/Alias'
import { Text } from '@starwhale/ui/Text'

export default function JobOverview() {
    const { job } = useJob()
    const { project } = useProject()
    const [t] = useTranslation()
    const projectId = project?.id

    const datasetUris =
        job?.datasetList
            ?.map((v) => `project/${project?.name}/dataset/${v?.name}/version/${v?.version?.name}`)
            .join(',') || '-'

    const items = [
        {
            key: 'id',
            value: job?.uuid ?? '-',
        },
        {
            label: t('Job ID'),
            key: 'sys/id',
            value: job?.id ?? '-',
        },
        {
            label: t('Resource Pool'),
            key: 'sys/resource_pool',
            value: job?.resourcePool,
        },
        {
            label: t('sth name', [t('Model')]),
            key: 'sys/model_name',
            value: job?.modelName,
        },
        {
            key: 'sys/model_uri',
            value: `project/${project?.name}/model/${job?.modelName}/version/${job?.modelVersion}`,
        },
        {
            label: t('Model Version'),
            key: 'sys/model_version',
            value: job?.modelVersion,
        },
        {
            key: 'sys/model_version_id',
            value: job?.model?.version?.id,
        },
        {
            label: t('Elapsed Time'),
            key: 'sys/duration_ms',
            value: typeof job?.duration === 'string' ? '-' : durationToStr(job?.duration as any),
        },
        {
            label: t('Created'),
            key: 'sys/created_time',
            value: job?.createdTime && job?.createdTime > 0 && formatTimestampDateTime(job?.createdTime),
        },
        {
            label: t('End Time'),
            key: 'sys/finished_time',
            value: job?.stopTime && job?.stopTime > 0 ? formatTimestampDateTime(job?.stopTime) : '-',
        },
        {
            label: t('Status'),
            key: 'sys/job_status',
            value: job?.jobStatus && <JobStatus key='jobStatus' status={job.jobStatus as any} />,
        },
        {
            key: 'sys/runtime_name',
            value: job?.runtime?.name,
        },
        {
            key: 'sys/runtime_uri',
            value: `project/${project?.name}/runtime/${job?.runtime?.name}/version/${job?.runtime?.version?.name}`,
        },
        {
            key: 'sys/runtime_version',
            value: job?.runtime?.version?.name,
        },
        {
            key: 'sys/runtime_version_id',
            value: job?.runtime?.version?.id,
        },
        {
            key: 'sys/runtime_version_alias',
            value: <Alias alias={job?.runtime?.version?.alias} />,
        },
        {
            key: 'sys/model_version_alias',
            value: <Alias alias={job?.model?.version?.alias} />,
        },
        {
            key: 'sys/step_spec',
            value: <Text tooltip={<pre>{job?.stepSpec}</pre>}>{job?.stepSpec}</Text>,
        },
        {
            key: 'sys/name',
            value: job?.jobName,
        },
        {
            key: 'sys/owner_id',
            value: job?.owner?.id,
        },
        {
            key: 'sys/owner_name',
            value: job?.owner?.name,
        },
        {
            key: 'sys/project_id',
            value: projectId,
        },
        {
            key: 'sys/dataset_uris',
            value: <Text tooltip={<pre>{datasetUris}</pre>}>{datasetUris}</Text>,
        },
    ].sort((a, b) => {
        return a?.key?.localeCompare(b?.key)
    })

    return (
        <div
            className='flex-column '
            style={{
                fontSize: '14px',
                gridTemplateColumns: 'minmax(160px, max-content) 1fr',
                display: 'grid',
                overflow: 'auto',
                gridAutoRows: '44px',
            }}
        >
            {items.map((v) => (
                <React.Fragment key={v?.key}>
                    <div
                        style={{
                            color: 'rgba(2,16,43,0.60)',
                            borderBottom: '1px solid #EEF1F6',
                            display: 'flex',
                            alignItems: 'center',
                        }}
                    >
                        {v?.key}
                    </div>

                    <div
                        className='line-clamp'
                        style={{
                            borderBottom: '1px solid #EEF1F6',
                            paddingLeft: '20px',
                            display: 'flex',
                            alignItems: 'center',
                        }}
                    >
                        {v?.value}
                    </div>
                </React.Fragment>
            ))}
        </div>
    )
}
