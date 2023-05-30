package com.dkanen.lecs

import kotlin.reflect.KClass

class World {
    var entityCounter = 0
    // Quickly find an entity's archetype
    val entityIndex = mutableMapOf<EntityId, Entity>()
    // Quickly look up the id of a component by its class
    val kClassIndex = mutableMapOf<KClass<*>, ComponentId>()

    // Allows World to quickly determine if a component is in an archetype and where it is.
    // There are fewer components than archetypes, since archetypes are combinations of components, so index by component
    val componentIndex = mutableMapOf<ComponentId, ArchetypeMap>()

    /**
     * The root Entity is the first Entity created. It has no Components, so it's Archetype is the empty Archetype. This
     * makes it possible to use the root Entity to get the empty Archetype and search for other Archetypes from there.
     */
    val rootEntity: EntityId

    /**
     * The empty Archetype has no Components. This makes it possible to follow its edges to any Archetype. Just have to
     * make sure to add each individual Component to the empty Archetype.
     */
    val emptyArchetypeEntity: EntityId

    /**
     * This Component tags a Component as a Component. You can use it to find Components.
     */
    val metaComponentEntity: EntityId

    /**
     * This Component tags an Archetype as an Archetype. You can use it to find Archetypes.
     */
    val metaArchetypeEntity: EntityId

    val metaArchetypeArchetype: ArchetypeId

    /**
     * This Component tags a System as a System. You can use it to find Systems.
     */
    val metaSystemEntity: EntityId

    init {
        rootEntity = entityId()
        emptyArchetypeEntity = entityId()
        val metaArchetypeIds = initEmptyArchetype()
        metaArchetypeEntity = metaArchetypeIds.first
        metaArchetypeArchetype = metaArchetypeIds.second

        metaComponentEntity = createMetaComponent(MetaComponent::class)

        addComponent(metaArchetypeEntity, MetaComponent::class)
        addComponent(metaArchetypeArchetype, MetaArchetype::class)

        metaSystemEntity = createEntity()
        addComponent(metaSystemEntity, MetaComponent::class)
    }

    private fun entityId(): EntityId = entityCounter++

    private fun initEmptyArchetype(): Pair<ComponentId, ArchetypeId> {
        // The Component that tags an Archetype as an Archetype
        val metaArchetypeId = entityId()
        // The Archetype that holds all the Archetypes, is also an Archetype so it's type is MetaArchetype
        val metaArchetypeArchetype = Archetype(entityId(), mutableListOf(metaArchetypeId), mutableListOf())
        kClassIndex[MetaArchetype::class] = metaArchetypeId

        val archetype = Archetype(
            id = emptyArchetypeEntity,
            type = mutableListOf(),
            rows = mutableListOf(mutableListOf(null), mutableListOf(null)), // root entity & empty archetype
            edges = mutableMapOf(Pair(metaArchetypeId, ArchetypeEdge(add = metaArchetypeArchetype)))
        )
        entityIndex[rootEntity] = Entity(archetype, 0)
        entityIndex[emptyArchetypeEntity] = Entity(archetype, 1)
        entityIndex[metaArchetypeId] = Entity(archetype, 2)
        entityIndex[metaArchetypeArchetype.id] = Entity(archetype, 3)

        return Pair(metaArchetypeId, metaArchetypeArchetype.id)
    }

    /**
     * Creates 2 entities, one for the component and one for the archetype
     * Post condition: the component exists as an entity
     */
    private fun createMetaComponent(component: KClass<*>): ComponentId = findOrCreateComponent(component).let { storeComponent(it, it) }

    /**
     * Post condition: the entity has the component
     */
    fun addComponent(entityId: EntityId, component: KClass<*>): ComponentId = storeComponent(entityId, findOrCreateComponent(component))

    fun createEntity(archetype: Archetype = emptyArchetype()): EntityId = createEntity(entityId(), archetype)

    private fun storeComponent(entityId: EntityId, componentId: ComponentId): ComponentId {
        entityIndex[entityId]?.let {
            addComponentToEntity(it, componentId)
            updateComponentIndex(componentId, it.archetype)
        } ?: throw EntityNotFoundException(entityId)

        return componentId
    }

    /**
     * Currently, this iterates over every component even if the component is already indexed.
     *
     * Post condition: each component in the archetype has it's column stored in the index
     */
    private fun updateComponentIndex(componentId: ComponentId, archetype: Archetype) {
        // If the archetype id is not in the component index, then it's safe to assume all the components in the archetype have been indexed.
        if (componentIndex[componentId]?.get(archetype.id) != null) {
            return
        }

        archetype.type.forEachIndexed { i: Int, indexedComponent: ComponentId ->
            componentIndex.getOrPut(indexedComponent) { mutableMapOf() }[archetype.id] = ArchetypeRecord(i)
        }
    }

    /**
     * Creates the entity by putting it in the entity index.
     * Uses the archetype of the root entity, since it's always the empty archetype.
     * The row is null because the root entity never has components.
     *
     * Post condition: the entity exists in the entity index.
     */
    private fun createEntity(entityId: EntityId, archetype: Archetype = emptyArchetype()): EntityId {
        // All entities start out in the empty Archetype. Then by maintaining the list of add edges on the for each
        // component on the empty, it's always possible to find a path to the first component added.
        entityIndex[entityId] = Entity(archetype, row = archetype.createEmptyRow())
        return entityId
    }

    private fun emptyArchetype(): Archetype {
        return entityIndex[rootEntity]!!.archetype
    }

    private fun entityDoesNotExist(entityId: EntityId) = entityIndex[entityId] == null

    /**
     * If the component has been seen before, use its id, otherwise create a new id and add it to the index
     */
    private fun findOrCreateComponent(component: KClass<*>) = kClassIndex[component] ?: run {
        val id = createEntity()
        kClassIndex[component] = id
        addComponent(id, MetaComponent::class)
        id
    }

    fun addSystem(selector: List<ComponentId>, lamda: (List<Component>) -> Unit): SystemId {
        val systemId = createEntity()
        addComponent(systemId, System::class)
        val system = System(selector, lamda)
        setComponent(systemId, system)
        return systemId
    }

    /**
     * Only allows selecting one component right now. Not terribly useful, yet.
     */
    fun select(selector: List<ComponentId>): List<Component> {
        val componentId = selector.first()

        val selected: MutableList<Component> = mutableListOf()

        entityIndex[rootEntity]?.archetype?.edges?.get(componentId)?.add?.let { archetype: Archetype ->
            val componentPosition = archetype.type.indexOf(componentId) //TODO: get the position of each selected component
            archetype.rows.forEach { components: MutableList<Any?> ->
                selected.add(components[componentPosition] as Component)
            }
        }

        return selected
    }

    fun process(systemId: SystemId) {
        val system = getComponent(systemId, System::class)

        val results = select(system!!.selector)
        results.forEach { component: Component ->
            system.update(listOf(component))
        }
    }

    /**
     * If the current archetype does not have the component:
     *  1. Create a new archetype with the component.
     *  2. Copy the components from the old archetype to the new archetype.
     *  3. Update the record to point to the new archetype and row.
     * If the current archetype does have the component:
     *  1. Create a row for it in its archetype.
     *
     *  Post condition: the record has a row in an archetype that has the component.
     */
    private fun addComponentToEntity(entity: Entity, componentId: ComponentId): Entity {
        return if (recordLacksComponent(entity, componentId)) {
            val destinationArchetype = addToArchetype(entity.archetype, componentId)

            // copy the component data from the old archetype to the new archetype
            entity.row = moveEntity(entity.archetype, entity.row, destinationArchetype)

            // update the record to point to the new archetype
            entity.archetype = destinationArchetype
            entity
        } else {
            // The Component already exists in the Archetype
            // If the row is already set then we're attempting to add a component that already exists.
            // If not then this is a new record and the row should be the next available spot in the Archetype.
            entity
        }
    }

    /**
     * Returns true if the record does not have the component, false otherwise.
     */
    private fun recordLacksComponent(entity: Entity, componentId: ComponentId): Boolean {
        return componentIndex[componentId]?.get(entity.archetype.id) == null
    }


    fun <T: Component> setComponent(entityId: EntityId, component: T) {
        val entity: Entity = entityIndex[entityId] ?: run {
            addComponent(entityId, component::class)
            entityIndex[entityId]!!
        }
        val archetype: Archetype = entity.archetype
        val columnId: Int = archetype.type.indexOf(kClassIndex[component::class]!!)
        val row: Int = entity.row

        archetype.rows[row][columnId] = component
    }

    fun <T: Any> getComponent(entityId: EntityId, component: KClass<T>): T? {
        val componentId = kClassIndex[component] ?: return null
        return getComponent(entityId, componentId)
    }

    fun <T> getComponent(entityId: EntityId, component: ComponentId): T? {
        val entity: Entity = entityIndex[entityId] ?: return null
        val archetype: Archetype = entity.archetype

        // check if the archetype has the component
        if (!archetype.type.contains(component)) return null

        // find the entity's component
        @Suppress("UNCHECKED_CAST")
        entity.row?.let { row ->
            return archetype.rows[row][archetype.type.indexOf(component)] as T
        }
        return null
    }

    private fun addToArchetype(archetype: Archetype, componentId: ComponentId): Archetype {
        return archetype.edges[componentId]?.add ?: run {
            // With a new component, a different archetype is needed.
            val destination = createOrUpdateArchetype(archetype.type, componentId)
            // update the old archetype to provide a path to the new archetype
            if (archetype.edges[componentId] == null) {
                archetype.edges[componentId] = ArchetypeEdge()
            }
            archetype.edges[componentId]!!.add = destination

            // update the new archetype to provide a path back to the old archetype
            if (destination.edges[componentId] == null) {
                destination.edges[componentId] = ArchetypeEdge()
            }
            destination.edges[componentId]!!.remove = archetype

            destination
        }
    }

    private fun createOrUpdateArchetype(type: Type, component: ComponentId): Archetype {
        val newType = (type + component).sorted().toMutableList()
        return findArchetype(newType) ?: createArchetype(newType)
    }

    private fun createArchetype(type: Type): Archetype {
        val id = createEntity()
        addComponent(id, MetaArchetype::class)
        return Archetype(id, type, mutableListOf(), mutableMapOf())
    }

    private fun findArchetype(type: Type): Archetype? {
        var foundArchetype: Archetype? = null
        type.forEach { componentId: ComponentId ->
            foundArchetype = foundArchetype?.edges?.get(componentId)?.add ?: entityIndex[rootEntity]?.archetype?.edges?.get(componentId)?.add
        }
        return foundArchetype
    }

    private fun moveEntity(srcArchetype: Archetype, srcRowId: Int, dstArchetype: Archetype): RowId = srcArchetype.rowAtOrFail(srcRowId).let { srcRow: Row ->
        val dstRow = dstArchetype.nullRow()
        // map the components from the old archetype to the new archetype
        // do it in addArchetype order so the list can expand from the left: [A], [A, B], [A, B, C]
        dstArchetype.type.forEachIndexed { i: Int, addArchetypeComponentId: ComponentId ->
            val index: Int = srcArchetype.type.lastIndexOf(addArchetypeComponentId)
            if (srcRow.isNotEmpty() && index >= 0) {
                dstRow[i] = srcRow[index]
            }
        }
        // Remove the components from the old archetype
        srcArchetype.remove(srcRowId)
        // Add the components to the new archetype
        dstArchetype.insertOrFail(dstRow)
    }

    private fun createRow(addArchetype: Archetype): RowId {
        val newRow = addArchetype.nullRow()
        // Add the components to the new archetype
        return addArchetype.insertOrFail(newRow)
    }

    fun hasComponent(entityId: EntityId, component: KClass<*>): Boolean {
        val componentId = kClassIndex[component] ?: return false
        return hasComponent(entityId, componentId)
    }

    fun hasComponent(entityId: EntityId, componentId: ComponentId): Boolean {
        val entity: Entity = entityIndex[entityId] ?: return false
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return false
        return archetypeMap[entity.archetype.id] != null
    }

    fun findArchetypes(component: KClass<*>): List<ArchetypeId> {
        val componentId = kClassIndex[component] ?: return emptyList()
        return findArchetypes(componentId)
    }

    fun findArchetypes(componentId: ComponentId): List<ArchetypeId> {
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return emptyList()
        //TODO: create an archetype index to speed this up
        return entityIndex.toList().filter { (_, entity: Entity) -> archetypeMap[entity.archetype.id] != null }.map { it.first }
    }

    fun identify(entityId: EntityId): String {
        when(entityId) {
            rootEntity -> return "root entity"
            emptyArchetypeEntity -> return "empty archetype entity"
            metaArchetypeEntity -> return "meta archetype entity"
            metaComponentEntity -> return "meta component entity"
            metaSystemEntity -> return "meta system entity"
            else -> return "unknown entity $entityId"
        }
    }

    fun archetypeFor(entity: EntityId): Archetype? {
        return entityIndex[entity]?.archetype
    }
}
