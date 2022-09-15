import React from 'react'
import Card from '@/components/Card'
import { usePage } from '@/hooks/usePage'
import { formatTimestampDateTime } from '@/utils/datetime'
import useTranslation from '@/hooks/useTranslation'
import Table from '@/components/Table'
import { useParams } from 'react-router-dom'
import { useFetchRuntimes } from '@/domain/runtime/hooks/useFetchRuntimes'
import User from '@/domain/user/components/User'
import { TextLink } from '@/components/Link'

export default function RuntimeListCard() {
    const [page] = usePage()
    const { projectId } = useParams<{ runtimeId: string; projectId: string }>()

    const runtimesInfo = useFetchRuntimes(projectId, page)

    const [t] = useTranslation()

    return (
        <Card title={t('Runtimes')}>
            <Table
                isLoading={runtimesInfo.isLoading}
                columns={[t('sth name', [t('Runtime')]), t('Runtime Version'), t('Owner'), t('Created'), t('Action')]}
                data={
                    runtimesInfo.data?.list.map((runtime) => {
                        return [
                            <TextLink key={runtime.id} to={`/projects/${projectId}/runtimes/${runtime.id}`}>
                                {runtime.name}
                            </TextLink>,
                            runtime.version?.name ?? '-',
                            runtime.owner && <User user={runtime.owner} />,
                            runtime.createdTime && formatTimestampDateTime(runtime.createdTime),
                            <TextLink key={runtime.id} to={`/projects/${projectId}/runtimes/${runtime.id}/versions`}>
                                {t('Version History')}
                            </TextLink>,
                        ]
                    }) ?? []
                }
                paginationProps={{
                    start: runtimesInfo.data?.pageNum,
                    count: runtimesInfo.data?.pageSize,
                    total: runtimesInfo.data?.total,
                    afterPageChange: () => {
                        runtimesInfo.refetch()
                    },
                }}
            />
        </Card>
    )
}
