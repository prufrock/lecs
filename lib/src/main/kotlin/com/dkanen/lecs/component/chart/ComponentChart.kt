package com.dkanen.lecs.component.chart

import kotlin.reflect.KClass

data class ComponentId(val id: Int): Comparable<ComponentId> {
    override fun compareTo(other: ComponentId) = this.id.compareTo(other.id)
}

data class ArchetypeId(val id: Int)

data class ArchetypeColumn(val id: Int)

data class Query(private val components: List<KClass<out Component>>): Iterable<KClass<out Component>> {

    fun isEmpty() = components.isEmpty()

    override fun iterator(): Iterator<KClass<out Component>> {
        return components.iterator()
    }
}

fun queryOf(vararg components: KClass<out Component>): Query = if (components.count() > 0) { Query(components.asList()) } else { Query(emptyList()) }

data class RowId(val id: Int, val archetypeId: ArchetypeId)

interface Component

interface ComponentChart {

    fun createRow(): RowId

    fun <T: Component> readComponent(rowId: RowId, type: KClass<T>): T

    fun deleteRow(rowId: RowId)

    fun addComponent(rowId: RowId, component: Component): RowId

    fun removeComponent(rowId: RowId, component: KClass<out Component>): RowId

    fun select(query: Query, read: (List<Component>, columns: List<ArchetypeColumn>) -> Unit)
}

class FixedComponentChart(private val archetypeFactory: ArchetypeFactory = ArchetypeFactory(500)): ComponentChart {

    private val root = createArchetype(ArchetypeId(0), listOf(), listOf())

    private val archetypes: MutableList<Archetype> = mutableListOf(root)

    private val components: MutableMap<KClass<out Component>, ComponentId> = mutableMapOf()

    private val componentArchetype: MutableMap<ComponentId, MutableMap<ArchetypeId, ArchetypeColumn>> = mutableMapOf()

    override fun createRow() = root.createRow()

    @Suppress("UNCHECKED_CAST")
    override fun <T: Component> readComponent(rowId: RowId, type: KClass<T>): T {
        return archetypes[rowId.archetypeId.id].read(rowId)[componentArchetype[components[type]]!![rowId.archetypeId]!!.id] as T
    }

    override fun deleteRow(rowId: RowId) {
        archetypes[rowId.archetypeId.id].delete(rowId)
    }

    override fun addComponent(rowId: RowId, component: Component): RowId {
        // 1. Get the ComponentId
        // 2. Get the Archetype.
        // 3. If it's already in the Archetype update the Component.
        // 4. Follow the add edge to the destination Archetype.
        //  4a. If the add edge exists follow it to the Archetype.
        //  4b. If the add edge is null create a new Archetype.
        // 5. Cut the Row from the current Archetype and add it to the new Archetype with the additional Component.
        // 6. Create a new Row with the new Archetype.
        // 7. Return the Row.
        val componentId = componentId(component)
        val archetype = archetypes[rowId.archetypeId.id]

        val column = column(componentId, archetype.id)
        return if (column != null) {
            archetype.update(rowId, column, component)
        } else {
            updateNearestArchetype(archetype, rowId, componentId, component)
        }
    }

    override fun removeComponent(rowId: RowId, component: KClass<out Component>): RowId {
        // 1. Explode if the component or archetype doesn't exist.
        // 2. Get the Archetype
        // 3. Follow the remove edge to the destination Archetype.
        //    2a. If the remove edge exists follow it to the Archetype.
        //    3b. If the remove edge is null create a new Archetype.
        // 4. Cut the row from the current Archetype and remove it from the destination Archetype without the component.
        // 5. Create a new Row with the destination Archetype.
        // 6. Return the Row.
        val componentId = components[component] ?: throw IllegalArgumentException("A component for type $component does not exist. Did you remove it before adding it to something?")
        val archetype = archetypes[rowId.archetypeId.id]
        val column = column(componentId, archetype.id) ?: throw IllegalArgumentException("The component[$componentId] for type $component does not have a column on the $archetype.")

        val row = archetype.delete(rowId)
        row.removeAt(column.id)
        val type = archetype.type.toMutableList()
        type.removeAt(column.id)
        val components = archetype.components.toMutableList()
        components.removeAt(column.id)


        return findOrCreateArchetype(archetype, componentId, row, type, components)
    }

    override fun select(query: Query, read: (List<Component>, columns: List<ArchetypeColumn>) -> Unit) {
        query(query) { row, columns ->
            read(row, columns)
        }
    }

    private fun query(query: Query, block: (List<Component>, columns: List<ArchetypeColumn>) -> Unit) {
        val queryComponentIds = sortedComponentIds(query)

        selectArchetypes(queryComponentIds).forEach { archetype ->
            archetype.forEach { row ->
                val columns = queryComponentIds.map { componentArchetype[it]!![archetype.id]!! }
                block(row, columns)
            }
        }
    }

    private fun selectArchetypes(queryComponentIds: List<ComponentId>): List<Archetype> {
        if (queryComponentIds.isEmpty()) {
            return emptyList()
        }

        return componentArchetype[queryComponentIds.first()]?.map { archetypeRecord ->
            val archetype = archetypes[archetypeRecord.key.id]
            //TODO: O(n) comparison of the elements. Query caching?
            if (queryComponentIds.all { it in archetype.type }) {
                archetype
            } else {
                null
            }
        }?.filterNotNull() ?: emptyList()
    }

    private fun sortedComponentIds(query: Query) = query.mapNotNull { components[it] }.sortedBy { it.id }

    private fun updateNearestArchetype(
        archetype: Archetype,
        rowId: RowId,
        componentId: ComponentId,
        component: Component
    ): RowId {
        val row = archetype.delete(rowId)
        val typedComponents = (archetype.type + componentId).alignedWith(row + component)

        return findOrCreateArchetype(
            archetype,
            componentId,
            typedComponents.row(),
            typedComponents.type(),
            archetype.components + component::class
        )
    }

    private fun findOrCreateArchetype(
        archetype: Archetype,
        componentId: ComponentId,
        row: Row,
        type: List<ComponentId>,
        components: List<KClass<out Component>>
    ): RowId {
        val nextArchetypeId = archetype.edges[componentId]
        val newRowId: RowId
        if (nextArchetypeId != null) {
            newRowId = archetypes[nextArchetypeId.id].insert(row)
        } else {
            val nextArchetype = nearestNeighborWithType(type, components)
            newRowId = nextArchetype.insert(row)
        }
        return newRowId
    }

    private fun nearestNeighborWithType(type: List<ComponentId>, components: List<KClass<out Component>>): Archetype {
        var nextArchetype = root
        type.forEachIndexed { index, componentId ->
            val next = nextArchetype.edges[componentId]
            nextArchetype = if (next == null) {
                createArchetypeForType(
                    nextArchetype,
                    type.subList(0, index + 1),
                    components
                )
            } else {
                archetypes[next.id]
            }
        }
        return nextArchetype
    }

    private fun createArchetypeForType(
        previousArchetype: Archetype,
        type: List<ComponentId>,
        components: List<KClass<out Component>>
    ): Archetype {
        val archetypeId = ArchetypeId(archetypes.count())
        type.forEachIndexed { column, newComponentId ->
            componentArchetype[newComponentId] = mutableMapOf(archetypeId to ArchetypeColumn(column))
        }
        val newArchetype = createArchetype(archetypeId, type, components)
        archetypes.add(newArchetype)
        // add the last component to the previous archetype go to the new archetype
        previousArchetype.edges[type.last()] = newArchetype.id
        // remove the last component from the new archetype go to the old archetype
        newArchetype.edges[type.last()] = previousArchetype.id
        return newArchetype
    }

    private fun componentId(component: Component): ComponentId = components[component::class] ?: createComponent(component)

    private fun createComponent(component: Component) = ComponentId(components.count()).also { components[component::class] = it }

    private fun column(componentId: ComponentId, archetypeId: ArchetypeId): ArchetypeColumn? = componentArchetype[componentId]?.get(archetypeId)

    private fun createArchetype(id: ArchetypeId, type: List<ComponentId>, components: List<KClass<out Component>>) = archetypeFactory.create(id, type, components)
}

data class Archetype(
    val id: ArchetypeId,
    val type: List<ComponentId>,
    private val table: Table
): Iterable<Row> {
    val edges: MutableMap<ComponentId, ArchetypeId> = mutableMapOf()

    val components
        get() = table.components

    fun createRow(): RowId = RowId(table.create(), id)

    fun read(rowId: RowId): Row {
        return table.read(rowId.id).toMutableList()
    }

    fun delete(rowId: RowId): Row {
        val row = table.read(rowId.id).toMutableList()
        table.delete(rowId.id)
        return row
    }

    fun update(rowId: RowId, column: ArchetypeColumn, component: Component): RowId {
        table.update(rowId.id, column.id, component)
        return rowId
    }

    fun insert(row: Row): RowId {
        return RowId(table.insert(row), id)
    }

    override fun iterator(): Iterator<Row> = table.iterator()
}

class ArchetypeFactory(private val size: Int) {
    fun create(id: ArchetypeId, type: List<ComponentId>, components: List<KClass<out Component>>): Archetype {
        return Archetype(id, type, SparseArrayTable(size, components))
    }
}

fun <T: Comparable<T>, U> List<T>.alignedWith(other: List<U>): List<Pair<T, U>> = zip(other).sortedBy { it.first }

fun List<TypedComponent>.type(): List<ComponentId> = map{it.first}
fun List<TypedComponent>.row(): MutableList<Component> = map{it.second}.toMutableList()

typealias Row = MutableList<Component>
typealias TypedComponent = Pair<ComponentId, Component>