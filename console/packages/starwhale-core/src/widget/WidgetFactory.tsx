import React from 'react'
import { WidgetGroupType, WidgetProps } from '../types'
import WidgetPlugin from './WidgetPlugin'
import { generateId } from '../utils/generators'

export type DerivedPropertiesMap = Record<string, string>
export type WidgetType = typeof WidgetFactory.widgetTypes[number]

class WidgetFactory {
    static widgetTypes: Record<string, string> = {}

    static widgetMap: Map<WidgetType, WidgetPlugin> = new Map()

    static register(widgetType: string, widget: WidgetPlugin) {
        if (!this.widgetTypes[widgetType]) {
            this.widgetTypes[widgetType] = widgetType
            this.widgetMap.set(widgetType, widget)
        }
    }

    static getWidgetTypes(): WidgetType[] {
        return Array.from(this.widgetMap.keys())
    }

    static getPanels() {
        return Array.from(this.widgetMap.values())
            .filter((plugin) => plugin.defaults?.group === WidgetGroupType.PANEL)
            .map((plugin) => plugin.defaults)
    }

    static getWidget(widgetType: WidgetType) {
        if (!this.widgetTypes[widgetType]) return null
        return this.widgetMap.get(widgetType)
    }

    static newWidget(widgetType: WidgetType) {
        if (!this.widgetMap.has(widgetType)) return null
        const widget = this.widgetMap.get(widgetType) as WidgetPlugin
        const id = generateId(widget.defaults?.group ?? '')

        return {
            defaults: widget.defaults,
            overrides: { id },
            node: {
                type: widgetType,
                id,
            },
        }
    }
}

export default WidgetFactory