import BusyPlaceholder from '@starwhale/ui/BusyLoaderWrapper/BusyPlaceholder'
import { useParseConfusionMatrix } from '@starwhale/core/datastore/hooks/useParseDatastore'
import React from 'react'
import { WidgetConfig, WidgetGroupType, WidgetRendererProps } from '@starwhale/core/types'
import { WidgetPlugin } from '@starwhale/core/widget'
import { getHeatmapConfig } from '@starwhale/ui/Plotly/utils'
import { useDatastoreDecodeRecords } from '@starwhale/core/datastore'

const PlotlyViewer = React.lazy(() => import(/* webpackChunkName: "PlotlyViewer" */ '@starwhale/ui/Plotly'))

export const CONFIG: WidgetConfig = {
    type: 'ui:panel:confusion_matrix',
    group: WidgetGroupType.PANEL,
    name: 'Confusion Matrix',
}

function PanelConfusionMatrixWidget(props: WidgetRendererProps<any, any>) {
    const { fieldConfig, data = {} } = props
    const { data: formData } = fieldConfig ?? {}
    const title = formData?.chartTitle ?? ''

    const $records = useDatastoreDecodeRecords(data?.records)

    const { labels, binarylabel } = useParseConfusionMatrix({
        ...data,
        records: $records,
    })
    const heatmapData = getHeatmapConfig(title, labels as any, binarylabel)

    return (
        <React.Suspense fallback={<BusyPlaceholder />}>
            <PlotlyViewer data={heatmapData} />
        </React.Suspense>
    )
}

const widget = new WidgetPlugin(PanelConfusionMatrixWidget, CONFIG)

export default widget
