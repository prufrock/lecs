package com.dkanen.lecs

import kotlin.reflect.KClass

class World {
    var entityCounter = 0
    // Quickly find an entity's archetype
    val entityIndex = mutableMapOf<EntityId, Record>()
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

        metaSystemEntity = createEntity(name = "meta-system")
        addComponent(metaSystemEntity, MetaComponent::class)

        findOrCreateComponent(Id::class)
        findOrCreateComponent(Name::class)
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
        entityIndex[rootEntity] = Record(archetype = archetype, row = 0)
        entityIndex[emptyArchetypeEntity] = Record(archetype = archetype, row = 1)
        entityIndex[metaArchetypeId] = Record(archetype, row = 2)
        entityIndex[metaArchetypeArchetype.id] = Record(archetype = archetype, row = 3)

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

    fun createEntity(archetype: Archetype = emptyArchetype(), name: String): EntityId = createEntity(entityId(), name, archetype)

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
    private fun createEntity(id: EntityId, name: String, archetype: Archetype = emptyArchetype()): EntityId {
        // All entities start out in the empty Archetype. Then by maintaining the list of add edges on the for each
        // component on the empty, it's always possible to find a path to the first component added.
        entityIndex[id] = Record(archetype = archetype, row = archetype.createEmptyRow())
        return id
    }

    private fun emptyArchetype(): Archetype {
        return entityIndex[rootEntity]!!.archetype
    }

    private fun entityDoesNotExist(entityId: EntityId) = entityIndex[entityId] == null

    /**
     * If the component has been seen before, use its id, otherwise create a new id and add it to the index
     */
    private fun findOrCreateComponent(component: KClass<*>) = kClassIndex[component] ?: run {
        val id = createEntity(name = component.simpleName ?: "unknown")
        kClassIndex[component] = id
        addComponent(id, MetaComponent::class)
        id
    }

    fun addSystem(name: String, selector: List<ComponentId>, lamda: (List<Component>) -> Unit): SystemId {
        val systemId = createEntity(name = name)
        addComponent(systemId, System::class)
        val system = System(selector, lamda)
        setComponent(systemId, system)
        return systemId
    }

    /**
     * Only allows selecting one component right now. Not terribly useful, yet.
     */
    fun select(selector: List<ComponentId>): List<List<Component>> {
        val selected: MutableList<MutableList<Component>> = mutableListOf()

        findArchetype(selector.toMutableList())?.let { archetype: Archetype ->
            val componentPositions = archetype.indexOfComponents(selector)
            archetype.rows.forEach { components: MutableList<Any?> ->
                val selectedComponents = mutableListOf<Component>()
                componentPositions.forEach { componentPosition: Int ->
                    selectedComponents.add(components[componentPosition] as Component)
                }
                selected.add(selectedComponents)
            }
        }

        return selected
    }

    fun process(systemId: SystemId) {
        val system = getComponent<System>(systemId)

        val results = select(system!!.selector)
        results.forEach { components: List<Component> ->
            system.update(components)
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
    private fun addComponentToEntity(record: Record, componentId: ComponentId): Record {
        return if (recordLacksComponent(record, componentId)) {
            val destinationArchetype = addToArchetype(record.archetype, componentId)

            // copy the component data from the old archetype to the new archetype
            record.row = moveEntity(record.archetype, record.row, destinationArchetype)

            // update the record to point to the new archetype
            record.archetype = destinationArchetype
            record
        } else {
            // The Component already exists in the Archetype
            // If the row is already set then we're attempting to add a component that already exists.
            // If not then this is a new record and the row should be the next available spot in the Archetype.
            record
        }
    }

    /**
     * Returns true if the record does not have the component, false otherwise.
     */
    private fun recordLacksComponent(record: Record, componentId: ComponentId): Boolean {
        return componentIndex[componentId]?.get(record.archetype.id) == null
    }


    fun <T: Component> setComponent(entityId: EntityId, component: T) {
        val record: Record = run {
            addComponent(entityId, component::class)
            entityIndex[entityId] ?: throw EntityNotFoundException(entityId)
        }
        val archetype: Archetype = record.archetype
        val columnId: Int = archetype.type.indexOf(kClassIndex[component::class]!!)
        val row: Int = record.row

        archetype.rows[row][columnId] = component
    }

    inline fun <reified T: Any> getComponent(entityId: EntityId): T? {
        val componentId = kClassIndex[T::class] ?: return null
        return getComponent(entityId, componentId)
    }

    fun <T> getComponent(entityId: EntityId, component: ComponentId): T? {
        val record: Record = entityIndex[entityId] ?: return null
        val archetype: Archetype = record.archetype

        // check if the archetype has the component
        if (!archetype.type.contains(component)) return null

        // find the entity's component
        @Suppress("UNCHECKED_CAST")
        record.row?.let { row ->
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
        //TODO: limit the name of an archetype to some number of components
        val id = createEntity(name = type.map{ identify(it) }.joinToString(",") + " Archetype")
        addComponent(id, MetaArchetype::class)
        return Archetype(id, type, mutableListOf(), mutableMapOf())
    }

    /**
     * TODO: return a list of archetypes that match the type
     */
    private fun findArchetype(type: Type): Archetype? {
        var foundArchetype: Archetype? = null
        entityIndex.toList().forEach {
            val record = it.second
            // brute force search for the matching archetype
            if (record.archetype.type.containsAll(type)) {
                foundArchetype = record.archetype
            }
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

    private fun hasComponent(entityId: EntityId, componentId: ComponentId): Boolean {
        val record: Record = entityIndex[entityId] ?: return false
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return false
        return archetypeMap[record.archetype.id] != null
    }

    fun findArchetypes(component: KClass<*>): List<ArchetypeId> {
        val componentId = kClassIndex[component] ?: return emptyList()
        return findArchetypes(componentId)
    }

    private fun findArchetypes(componentId: ComponentId): List<ArchetypeId> {
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return emptyList()
        //TODO: create an archetype index to speed this up
        return entityIndex.toList().filter { (_, record: Record) -> archetypeMap[record.archetype.id] != null }.map { it.first }
    }

    fun identify(entityId: EntityId): String {
        return when(entityId) {
            rootEntity -> "root entity"
            emptyArchetypeEntity -> "empty archetype"
            metaArchetypeEntity -> "meta-archetype"
            metaComponentEntity -> "meta-component"
            metaSystemEntity -> "meta-system"
            else -> "unknown entity"
        }
    }

    fun archetypeFor(entity: EntityId): Archetype? {
        return entityIndex[entity]?.archetype
    }

    /**
     * Convert classes to a list of component ids.
     */
    fun componentToId(vararg xs: KClass<*>): List<ComponentId> {
        return xs.map { kClassIndex[it]!! }
    }
}
