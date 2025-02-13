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

    private var systemCounter = 1 // reserve 0

    fun createEntity(): Entity {
        val id = entityCounter++.eid
        val rowId = chart.createRow()
        entityMap[id] = rowId

        return Entity(id, this)
    }

    fun deleteEntity(id: EntityId) {
        val rowId = entityMap.remove(id) ?: throw EntityNotFoundException(id)
        chart.deleteRow(rowId)
    }

    fun addComponent(id: EntityId, component: Component) {
        val rowId = entityMap[id] ?: throw EntityNotFoundException(id)
        val newRow = chart.addComponent(rowId, component)
        entityMap[id] = newRow
    }

    fun <T:Component> deleteComponent(id: EntityId, type: KClass<T>) {
        val rowId = entityMap[id] ?: throw EntityNotFoundException(id)
        val newRow = chart.removeComponent(rowId, type)
        entityMap[id] = newRow
    }

    fun <T: Component> getComponent(id: EntityId, type: KClass<T>): T {
        val rowId = entityMap[id] ?: throw EntityNotFoundException(id)
        return chart.readComponent(rowId, type)
    }

    /**
     * The other select is easier to use, but I want to keep this one around for a little bit to compare performance.
     */
    @Suppress("unused")
    fun select(query: Query, block: (components: List<Component>, columns: List<ArchetypeColumn>) -> Unit) {
        chart.select(query, block)
    }

    fun select(query: Query, block: (components: Components) -> Unit) {
        chart.select(query) { components, columns ->
            block(Components(query, components, columns))
        }
    }

    fun createSystem(query: Query, block: (components: Components) -> Unit) = System(systemCounter++, query, this, block)
}

data class EntityId(val id: Int)

val Int.eid get() = EntityId(this)

data class Entity(val id: EntityId, private val world: World) {
    fun addComponent(component: Component) {
        world.addComponent(id, component)
    }

    fun <T: Component> deleteComponent(type: KClass<T>) {
        world.deleteComponent(id, type)
    }

    fun delete() {
        world.deleteEntity(id)
    }

    fun <T: Component> getComponent(type: KClass<T>): T = world.getComponent(id, type)
}

class Components(query: Query, private val components: List<Component>, private val columns: List<ArchetypeColumn>) {

    private val index: Map<KClass<out Component>, Int> =
        query.mapIndexed { index, componentClass -> componentClass to index }.toMap()

    @Suppress("UNCHECKED_CAST")
    fun <T: Component> get(type: KClass<T>): T  {
        val position = index.getOrElse(type) {
            throw ComponentNotFoundException(type.toString())
        }

        return components[columns[position].id] as T
    }
}