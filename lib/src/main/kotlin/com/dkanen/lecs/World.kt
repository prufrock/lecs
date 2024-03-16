package com.dkanen.lecs

import com.dkanen.lecs.component.chart.*
import kotlin.reflect.KClass

/**
 * The World has entity IDs it maps to RowIds. The EntityIds are stable and ever-increasing. RowIds change as entities
 * move between Archetypes.
 *
 * World returns Entity which is a thin wrapper over world to avoid having to keep passing entity id on each call.
 */
class World {

    private val chart = FixedComponentChart()
    private var entityCounter = 1 // reserve 0
    private val entityMap: MutableMap<EntityId, RowId> = mutableMapOf()

    fun createEntity(): Entity {
        val id = entityCounter++.eid
        val rowId = chart.createRow()
        entityMap[id] = rowId

        return Entity(id, this)
    }

    fun addComponent(id: EntityId, component: Component) {
        val rowId = entityMap[id] ?: throw EntityNotFoundException(id)
        val newRow = chart.addComponent(rowId, component)
        entityMap[id] = newRow
    }

    fun <T: Component> getComponent(id: EntityId, type: KClass<T>): T {
        val rowId = entityMap[id] ?: throw EntityNotFoundException(id)
        return chart.readComponent(rowId, type)
    }

    fun select(query: Query, block: (components: List<Component>, columns: List<ArchetypeColumn>) -> Unit) {
        chart.select(query, block)
    }
}

data class EntityId(val id: Int)

val Int.eid get() = EntityId(this)

data class Entity(val id: EntityId, private val world: World) {
    fun addComponent(component: Component) {
        world.addComponent(id, component)
    }

    fun <T: Component> getComponent(type: KClass<T>): T = world.getComponent(id, type)
}
