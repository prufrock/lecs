package com.dkanen.lecs

import kotlin.reflect.KClass

class World {
    var entityCounter = 0
    // Quickly find an entity's archetype
    val entityIndex = mutableMapOf<EntityId, Record>()
    // Quickly look up the id of a component by its class
    val kClassIndex = mutableMapOf<KClass<*>, ComponentId>()
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

        metaComponentEntity = addMetaComponent(MetaComponent::class)

        addComponent(metaArchetypeEntity, MetaComponent::class)
        addComponent(metaArchetypeArchetype, MetaArchetype::class)

        metaSystemEntity = entityId()
        addComponent(metaSystemEntity, MetaComponent::class)
    }

    fun entityId(): EntityId = entityCounter++

    private fun initEmptyArchetype(): Pair<ComponentId, ArchetypeId> {
        // The Component that tags an Archetype as an Archetype
        val metaArchetypeId = entityId()
        // The Archetype that holds all the Archetypes, is also an Archetype so it's type is MetaArchetype
        val metaArchetypeArchetype = Archetype(entityId(), mutableListOf(metaArchetypeId), mutableListOf())
        kClassIndex[MetaArchetype::class] = metaArchetypeId

        val archetype = Archetype(
            id = emptyArchetypeEntity,
            type = mutableListOf(),
            components = mutableListOf(),
            edges = mutableMapOf(Pair(metaArchetypeId, ArchetypeEdge(add = metaArchetypeArchetype)))
        )
        entityIndex[rootEntity] = Record(archetype, null)
        entityIndex[emptyArchetypeEntity] = Record(archetype, null)

        return Pair(metaArchetypeId, metaArchetypeArchetype.id)
    }

    /**
     * Creates 2 entities, one for the component and one for the archetype
     * Post condition: the component exists as an entity
     */
    fun addMetaComponent(component: KClass<*>): ComponentId {
        val componentId = kClassIndex[component] ?: run {
            val id = entityId()
            kClassIndex[component] = id
            id
        }

        var componentColumn: Int = 0
        // if there is no Record create one
        if (entityDoesNotExist(componentId)) {
            entityIndex[componentId] = Record(entityIndex[rootEntity]!!.archetype, row = 0)
        }

        // Create a record and either store the component in an existing archetype or create a new archetype
        val record: Record = updateRecord(entityIndex[componentId]!!, componentId)

        // Add placeholder in the Archetype's components list for the new component.
        if (record.archetype.components.lastIndex < record.row!!) {
            // TODO: consider finding a way to set this when the row is assigned to the record.
            record.archetype.components.add(mutableListOf())
        }

        // Determine which column the component ends up in.
        componentColumn = record.archetype.type.indexOf(componentId)

        // Fill the row with nulls up to the current column.
        while (record.archetype.components[record.row!!].lastIndex < componentColumn) {
            record.archetype.components[record.row!!].add(null)
        }

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
        val componentId = findOrCreateComponent(component)

        // TODO: Consider moving entity creation out of addComponent.
        if (entityDoesNotExist(entityId)) {
            createEntity(entityId)
        }

        val record: Record = updateRecord(entityIndex[entityId]!!, componentId)

        // Add placeholder in the Archetype's components list for the new component.
        if (record.archetype.components.lastIndex < record.row!!) {
            // TODO: consider finding a way to set this when the row is assigned to the record.
            record.archetype.components.add(mutableListOf())
        }

        // Determine which column the component ends up in.
        val componentColumn = record.archetype.type.indexOf(componentId)

        // Fill the row with nulls up to the current column.
        while (record.archetype.components[record.row!!].lastIndex < componentColumn) {
            record.archetype.components[record.row!!].add(null)
        }

        entityIndex[entityId] = record

        // Update the component index so the column of a component in an archetype can be found quickly.
        componentIndex[componentId]?.let { archetypeMap: ArchetypeMap ->
            componentIndex[componentId]?.set(record.archetype.id, ArchetypeRecord(componentColumn!!))
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

    /**
     * Creates the entity by putting it in the entity index.
     * Uses the archetype of the root entity, since it's always the empty archetype.
     * The row is null because the root entity never has components.
     *
     * Post condition: the entity exists in the entity index.
     */
    private fun createEntity(entityId: EntityId) {
        entityIndex[entityId] = Record(entityIndex[rootEntity]!!.archetype, row = null)
    }

    private fun entityDoesNotExist(entityId: EntityId) = entityIndex[entityId] == null

    /**
     * If the component has been seen before, use its id, otherwise create a new id and add it to the index
     */
    private fun findOrCreateComponent(component: KClass<*>) = kClassIndex[component] ?: run {
        val id = entityId()
        kClassIndex[component] = id
        addComponent(id, MetaComponent::class)
        id
    }

    fun addSystem(selector: List<ComponentId>, lamda: (List<Component>) -> Unit): SystemId {
        val systemId = entityId()
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
            val componentPosition = archetype.type.indexOf(componentId) //TODO: get the position of each select component
            archetype.components.forEach { components: MutableList<Any?> ->
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
     *  1. Create a row for it in it's archetype.
     *
     *  Post condition: the record has a row in an archetype that has the component.
     */
    private fun updateRecord(record: Record, componentId: ComponentId): Record {
        if (recordLacksComponent(record, componentId)) {
            val archetype = record.archetype
            val addArchetype = addToArchetype(archetype, componentId)

            // copy the component data from the old archetype to the new archetype
            val newRow = record.row?.let { row: Int ->
                moveEntity(archetype, row, addArchetype)
            } ?: createRow(addArchetype) ?: throw Exception("Failed to create entity")

            // update the record to point to the new archetype
            record.archetype = addArchetype
            // record needs to know where to find its components in the new archetype
            record.row = newRow
            return record
        } else {
            // The Component already exists in the Archetype
            // If the row is already set then we're attempting to add a component that already exists.
            // If not then this is a new record and the row should be the next available spot in the Archetype.
            if (record.row == null) {
                record.row = createRow(record.archetype) ?: throw Exception("Failed to create entity")
            }
            return record
        }
    }

    /**
     * Returns true if the record does not have the component, false otherwise.
     */
    private fun recordLacksComponent(record: Record, componentId: ComponentId): Boolean {
        return componentIndex[componentId]?.get(record.archetype.id) == null
    }


    fun <T: Component> setComponent(entityId: EntityId, component: T) {
        val record: Record = entityIndex[entityId] ?: run {
            addComponent(entityId, component::class)
            entityIndex[entityId]!!
        }
        val archetype: Archetype = record.archetype
        val columnId: Int = archetype.type.indexOf(kClassIndex[component::class]!!)
        val row: Int = record.row ?: archetype.components[columnId].count()

        archetype.components[row][columnId] = component
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

        // find the entity's component
        @Suppress("UNCHECKED_CAST")
        record.row?.let {row ->
            return archetype.components[row][archetype.type.indexOf(component)] as T
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
        val id = entityId()
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

    private fun moveEntity(archetype: Archetype, row: Int, addArchetype: Archetype): RowId? = archetype.components.elementAtOrNull(row)?.let { column: Column ->
        val newRow = mutableListOf<Any?>()
        // map the components from the old archetype to the new archetype
        // do it in addArchetype order so the list can expand from the left: [A], [A, B], [A, B, C]
        addArchetype.type.forEachIndexed { i: Int, addArchetypeComponentId: ComponentId ->
            val index: Int = archetype.type.lastIndexOf(addArchetypeComponentId)
            if (column.isNotEmpty() && index >= 0) {
                newRow.add(column[index])
            }
        }
        // Make sure the new row has enough columns
        //TODO: I think this can be changed so that the newRow is always the same size as the addArchetype.type then add the components
        newRow.fill(addArchetype.type.count())
        // Remove the components from the old archetype
        archetype.remove(row)
        // Add the components to the new archetype
        addArchetype.insert(newRow)
    }

    private fun createRow(addArchetype: Archetype): RowId? {
        val newRow = mutableListOf<Any?>()
        newRow.fill(addArchetype.type.count())
        // Add the components to the new archetype
        return addArchetype.insert(newRow)
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

    fun findArchetypes(component: KClass<*>): List<ArchetypeId> {
        val componentId = kClassIndex[component] ?: return emptyList()
        return findArchetypes(componentId)
    }

    fun findArchetypes(componentId: ComponentId): List<ArchetypeId> {
        val archetypeMap: ArchetypeMap = componentIndex[componentId] ?: return emptyList()
        //TODO: create an archetype index to speed this up
        return entityIndex.toList().filter { (_, record: Record) -> archetypeMap[record.archetype.id] != null }.map { it.first }
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
