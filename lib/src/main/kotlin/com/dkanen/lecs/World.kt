package com.dkanen.lecs

import kotlin.reflect.KClass

typealias EntityId = Int
typealias ComponentId = Int
typealias ArchetypeId = Int
typealias SystemId = Int
typealias Type = MutableList<ComponentId>

/**
 * In a language stricter about the size of array elements like Swift, c++ you might need this:
struct Column {
void *elements;      // buffer with component data
size_t element_size; // size of a single element
size_t count;        // number of elements
}
 */
typealias Column = MutableList<Any>

data class ArchetypeEdge(
    var add: Archetype? = null,
    var remove: Archetype? = null
)

data class Archetype(
    val id: ArchetypeId,
    val type: Type,
    val components: MutableList<Column>,
    /**
     *  This is a list of lists so this allows a vectorizable structure like this:
     *  [Velocity, Position]
     *  [Velocity, Position]
     *  [Velocity, Position]
     */
    val edges: MutableMap<ComponentId, ArchetypeEdge> = mutableMapOf()
) {
    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Archetype

        return id == other.id
    }
    override fun toString(): String = buildString {
        append("Archetype(id=$id, type=$type, components=$components")
    }
}
data class Record(var archetype: Archetype, var row: Int?) {
    override fun toString(): String {
        return buildString {
            append("Record(archetype=${archetype.id}, row=$row)")
        }
    }
}
data class ArchetypeRecord(val column: Int)
typealias ArchetypeMap = MutableMap<ArchetypeId, ArchetypeRecord>

interface Component
data class Position(var x: Double, val y: Double): Component
data class Velocity(val x: Double, val y: Double): Component
data class Health(val hp: Double): Component
data class System(val selector: List<ComponentId>, val update: (EntityId) -> Unit): Component
class MetaComponent(): Component
class MetaArchetype(): Component
class MetaSystem(): Component

class World {
    var entityCounter = 0
    // Quickly find an entity's archetype
    val entityIndex = mutableMapOf<EntityId, Record>()
    // Quickly look up the id of a component by its class
    val kClassIndex = mutableMapOf<KClass<*>, ComponentId>()
    // There are fewer components than archetypes, since archetypes are combinations of components, so index by component
    val componentIndex = mutableMapOf<ComponentId, ArchetypeMap>()
    val rootEntity: EntityId
    val emptyArchetypeEntity: EntityId
    val metaComponentEntity: EntityId
    val metaArchetypeEntity: EntityId
    val metaSystemEntity: EntityId

    init {
        rootEntity = entity()
        emptyArchetypeEntity = entity()
        initEmptyArchetype()
        metaComponentEntity = addMetaComponent(MetaComponent::class)
        setComponent(metaComponentEntity, MetaComponent())
        metaArchetypeEntity = addMetaComponent(MetaArchetype::class)
        setComponent(metaArchetypeEntity, MetaArchetype())
        metaSystemEntity = addMetaComponent(MetaSystem::class)
        setComponent(metaSystemEntity, MetaSystem())
    }

    fun entity(): EntityId {
        return entityCounter++
    }

    private fun initEmptyArchetype() {
        val archetype = Archetype(emptyArchetypeEntity, mutableListOf(), mutableListOf())
        entityIndex[rootEntity] = Record(archetype, null)
    }

    /**
     * Creates 2 entities, one for the component and one for the archetype
     * Post condition: the component exists as an entity
     */
    fun addMetaComponent(component: KClass<*>): ComponentId {
        val componentId = kClassIndex[component] ?: run {
            val id = entity()
            kClassIndex[component] = id
            id
        }

        var componentColumn: Int = 0
        // if there is no Record create one
        if (entityIndex[componentId] == null) {
            entityIndex[componentId] = Record(entityIndex[rootEntity]!!.archetype, row = 0)
        }

        // Create a record and either store the component in an existing archetype or create a new archetype
        val record: Record = updateRecord(entityIndex[componentId]!!, componentId)

        // Is it worth worrying about removing an archetype from a component if no entities have it?
        entityIndex[componentId] = record
        componentIndex[componentId]?.set(record.archetype.id, ArchetypeRecord(componentColumn!!)) ?: run {
            componentIndex[componentId] = mutableMapOf(record.archetype.id to ArchetypeRecord(componentColumn!!))
        }

        return componentId
    }

    /**
     * Post condition: the entity has the component
     */
    fun addComponent(entityId: EntityId, component: KClass<*>): ComponentId {
        // if the component has been seen before, use its id, otherwise create a new id and add it to the index
        val componentId = kClassIndex[component] ?: run {
            val id = entity()
            kClassIndex[component] = id
            //TODO: this entity needs to have the MetaComponent component added to it
            id
        }

        // if there is no Record create one
        if (entityIndex[entityId] == null) {
            entityIndex[entityId] = Record(entityIndex[rootEntity]!!.archetype, row = null)
        }

        // If the entity has a record, add the component to the archetype if it doesn't already have it, otherwise
        // create a new record with a new archetype.
        val record: Record = updateRecord(entityIndex[entityId]!!, componentId)

        val componentColumn = if (record.archetype.components.isEmpty()) { 0 } else { record.archetype.components.lastIndex }
        // Is it worth worrying about removing an archetype from a component if no entities have it?
        entityIndex[entityId] = record

        componentIndex[componentId]?.let { archetypeMap: ArchetypeMap ->

        } ?: run {
            componentIndex[componentId] = mutableMapOf(record.archetype.id to ArchetypeRecord(componentColumn!!))
        }

        record.archetype.type.forEachIndexed { i: Int, component: ComponentId ->
            if (componentIndex[component] == null) {
                componentIndex[component] = mutableMapOf()
            }
            componentIndex[component]?.let { archetypeMap: ArchetypeMap ->
                archetypeMap[record.archetype.id] = ArchetypeRecord(i)
            }
        }
        return componentId
    }

    fun addSystem(selector: List<ComponentId>, lamda: (EntityId) -> Unit): SystemId {
        val systemId = entity()
        addComponent(systemId, System::class)
        val system = System(selector, lamda)
        setComponent(systemId, system)
        return systemId
    }

    fun process(systemId: SystemId) {
        val system = getComponent(systemId, System::class)

        entityIndex.forEach { (entityId: EntityId, record: Record) ->
            if (record.archetype.type.contains(system!!.selector.first())) {
                system.update(entityId)
            }
        }
    }

    private fun updateRecord(record: Record, componentId: ComponentId): Record {
        // There could be an optimization where if the archetype is only used by this entity, then it can be modified in place.
        if (componentIndex[componentId]?.get(record.archetype.id) == null) {
            val archetype = record.archetype
            val addArchetype = addToArchetype(archetype, componentId)

            // copy the component data from the old archetype to the new archetype
            record.row?.let { row: Int ->
                moveEntity(archetype, row, addArchetype)
            }

            // update the record to point to the new archetype
            record.archetype = addArchetype
            // record needs to know where to find its components in the new archetype
            // TODO: if I have 2 empty components they point to the same row, but they should point to different rows, especially when the array is sparse
            record.row = if (addArchetype.components.isEmpty()) {
                0
            } else {
                addArchetype.components.lastIndex
            }
        }
        return record
    }

    fun <T: Component> setComponent(entityId: EntityId, component: T) {
        val record: Record = entityIndex[entityId] ?: run {
            addComponent(entityId, component::class)
            entityIndex[entityId]!!
        }
        val archetype: Archetype = record.archetype
        val columnId: Int = archetype.type.indexOf(kClassIndex[component::class]!!)
        val row: Int = record.row ?: archetype.components[columnId].count()
        if (archetype.components.count() <= columnId) {
            archetype.components.add(mutableListOf())
        }
        if (archetype.components[columnId].count() <= row) {
            archetype.components[columnId].add(component)
        } else {
            archetype.components[columnId][row] = component
        }
    }

    fun <T: Any> getComponent(entityId: EntityId, component: KClass<T>): T? {
        val componentId = kClassIndex[component] ?: return null
        return getComponent(entityId, componentId)
    }

    fun <T> getComponent(entityId: EntityId, component: ComponentId): T? {
        val record: Record = entityIndex[entityId] ?: return null
        val archetype: Archetype = record.archetype

        // check if the archetype has the component
        if (!archetype.type.contains(component)) return null

        // find the entities component
        @Suppress("UNCHECKED_CAST")
        record.row?.let {row ->
            return archetype.components[archetype.type.indexOf(component)][row] as T
        }
        return null
    }

    private fun addToArchetype(archetype: Archetype, componentId: ComponentId): Archetype {
        return archetype.edges[componentId]?.add ?: run {
            val newComponents = mutableListOf<Column>()
            val newArchetype = Archetype(entity(), (archetype.type + componentId).toMutableList(), newComponents, mutableMapOf())
            // update the old archetype to provide a path to the new archetype
            if (archetype.edges[componentId] == null) {
                archetype.edges[componentId] = ArchetypeEdge()
            }
            archetype.edges[componentId]!!.add = newArchetype

            // update the new archetype to provide a path back to the old archetype
            if (newArchetype.edges[componentId] == null) {
                newArchetype.edges[componentId] = ArchetypeEdge()
            }
            newArchetype.edges[componentId]!!.remove = archetype

            newArchetype
        }
    }

    private fun moveEntity(archetype: Archetype, row: Int, addArchetype: Archetype) {
        archetype.components.elementAtOrNull(row)?.let { column: Column ->
            val newColumns = mutableListOf<Any>()
            // map the components from the old archetype to the new archetype
            // do it in addArchetype order so the list can expand from the left: [A], [A, B], [A, B, C]
            addArchetype.type.forEachIndexed { i: Int, addArchetypeComponentId: ComponentId ->
                val index: Int = archetype.type.lastIndexOf(addArchetypeComponentId)
                if ( index >= 0) {
                    newColumns.add(column[index])
                }
            }
            // Add the components to the new archetype
            addArchetype.components.add(newColumns)
            // Remove the components from the old archetype
            archetype.components.removeAt(row)
        }
    }

    fun hasComponent(entityId: EntityId, component: KClass<*>): Boolean {
        val componentId = kClassIndex[component] ?: return false
        return hasComponent(entityId, componentId)
    }

    fun hasComponent(entityId: EntityId, componentId: ComponentId): Boolean {
        val record: Record = entityIndex[entityId] ?: return false
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return false
        return archetypeMap[record.archetype.id] != null
    }

    fun findArchetypes(component: KClass<*>): List<Archetype> {
        val componentId = kClassIndex[component] ?: return emptyList()
        return findArchetypes(componentId)
    }

    fun findArchetypes(componentId: ComponentId): List<Archetype> {
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return emptyList()
        //TODO: create an archetype index to speed this up
        return entityIndex.toList().map { it.second.archetype }.filter { archetype: Archetype -> archetypeMap[archetype.id] != null }
    }
}
