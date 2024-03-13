package com.dkanen.lecs

import com.dkanen.lecs.component.chart.Component
import com.dkanen.lecs.component.chart.FixedComponentChart
import com.dkanen.lecs.component.chart.RowId

/**
 * The World has entity IDs it maps to RowIds. The EntityIds are stable and ever-increasing. RowIds change as entities
 * move between Archetypes.
 *
 * World returns Entity which is a thin wrapper over world to avoid having to keep passing entity id on each call.
 */
class World {

    private val chart = FixedComponentChart()
    private var entityCounter = 0
    private val entityMap: MutableMap<EntityId, RowId> = mutableMapOf()

    fun createEntity(): Entity {
        val id = EntityId(entityCounter++)
        val rowId = chart.createRow()
        entityMap[id] = rowId

        return Entity(EntityId(entityCounter++), this)
    }

    fun addComponent(id: EntityId, component: Component) {
        chart.addComponent(entityMap[id]!!, component)
    }
}

data class EntityId(val id: Int)

data class Entity(val id: EntityId, private val world: World) {
    fun addComponent(component: Component) {
        world.addComponent(id, component)
    }
}
