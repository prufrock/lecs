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

data class RowId(val id: Int, val archetypeId: ArchetypeId)

interface Component

//TODO: naming is inconsistent
interface ComponentChart {

    fun createRow(): RowId

    fun <T: Component> readComponent(rowId: RowId, type: KClass<T>): T

    fun deleteRow(rowId: RowId)

    fun <T: Component> addComponent(rowId: RowId, component: T): RowId

    fun <T: Component> removeComponent(rowId: RowId, component: KClass<T>): RowId

    fun select(query: Query, read: (List<Component>, columns: List<ArchetypeColumn>) -> Unit)

    fun update(query: Query, transform: (List<Component>, columns: List<ArchetypeColumn>) -> List<Component>)
}

class DynamicComponentChart: ComponentChart {

    private val root = Archetype(ArchetypeId(0), listOf())

    private val archetypes: MutableList<Archetype> = mutableListOf(root)

    private val components: MutableMap<KClass<out Component>, ComponentId> = mutableMapOf()

    //TODO: I think this can be MutableMap<ComponentId, Pair<ArchetypeId, ArchetypeColumn>>
    private val componentArchetype: MutableMap<ComponentId, MutableMap<ArchetypeId, ArchetypeColumn>> = mutableMapOf()

    override fun createRow() = root.createRow()

    //TODO: Consider adding readComponentOrNull and readComponentOrDefault for situations where a safe call might be useful.
    @Suppress("UNCHECKED_CAST")
    override fun <T: Component> readComponent(rowId: RowId, type: KClass<T>): T {
        return archetypes[rowId.archetypeId.id].read(rowId)[componentArchetype[components[type]]!![rowId.archetypeId]!!.id] as T
    }

    override fun deleteRow(rowId: RowId) {
        archetypes[rowId.archetypeId.id].deleteRow(rowId)
    }

    override fun <T: Component> addComponent(rowId: RowId, component: T): RowId {
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

    override fun <T: Component> removeComponent(rowId: RowId, component: KClass<T>): RowId {
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

        val row = archetype.deleteRow(rowId)
        row.removeAt(column.id)
        val type = archetype.type.toMutableList()
        type.removeAt(column.id)

        return findOrCreateArchetype(archetype, componentId, row, type)
    }

    override fun select(query: Query, read: (List<Component>, columns: List<ArchetypeColumn>) -> Unit) {
        query(query) { _, row, columns ->
            read(row, columns)
        }
    }

    /**
     * Can't delete columns from a row during an update.
     */
    override fun update(query: Query, transform: (List<Component>, columns: List<ArchetypeColumn>) -> List<Component>) {
        query(query) { rowId, row, columns ->
            val newRow = transform(row, columns)
            archetypes[rowId.archetypeId.id].update(rowId, newRow.toMutableList())
        }
    }

    private fun query(query: Query, block: (rowId: RowId, List<Component>, columns: List<ArchetypeColumn>) -> Unit) {
        val queryComponentIds = sortedComponentIds(query)

        selectArchetypes(queryComponentIds).forEach { archetype ->
            archetype.forEachIndexed { i, row ->
                //TODO: after implementing Table see if `RowId(i, archetype.id)` can be cleaned up
                val rowId = RowId(i, archetype.id)
                val columns = queryComponentIds.map { componentArchetype[it]!![archetype.id]!! }
                block(rowId, row, columns)
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

    private fun <T : Component> updateNearestArchetype(
        archetype: Archetype,
        rowId: RowId,
        componentId: ComponentId,
        component: T
    ): RowId {
        val row = archetype.deleteRow(rowId)
        val typedComponents = (archetype.type + componentId).alignedWith(row + component)

        return findOrCreateArchetype(archetype, componentId, typedComponents.row(), typedComponents.type())
    }

    private fun findOrCreateArchetype(
        archetype: Archetype,
        componentId: ComponentId,
        row: Row,
        type: List<ComponentId>
    ): RowId {
        val nextArchetypeId = archetype.edges[componentId]
        val newRowId: RowId
        if (nextArchetypeId != null) {
            newRowId = archetypes[nextArchetypeId.id].insert(row)
        } else {
            val nextArchetype = nearestNeighborWithType(type)
            newRowId = nextArchetype.insert(row)
        }
        return newRowId
    }

    private fun nearestNeighborWithType(type: List<ComponentId>): Archetype {
        var nextArchetype = root
        type.forEachIndexed { index, componentId ->
            val next = nextArchetype.edges[componentId]
            nextArchetype = if (next == null) {
                createArchetypeForType(nextArchetype, type.subList(0, index + 1))
            } else {
                archetypes[next.id]
            }
        }
        return nextArchetype
    }

    private fun createArchetypeForType(previous: Archetype, type: List<ComponentId>): Archetype {
        val archetypeId = ArchetypeId(archetypes.count())
        type.forEachIndexed { column, newComponentId ->
            componentArchetype[newComponentId] = mutableMapOf(archetypeId to ArchetypeColumn(column))
        }
        val archetype = Archetype(archetypeId, type)
        archetypes.add(archetype)
        // add the last component to the previous archetype go to the new archetype
        previous.edges[type.last()] = archetype.id
        // remove the last component to the new archetype go to the old archetype
        archetype.edges[type.last()] = previous.id
        return archetype
    }

    private fun <T: Component> componentId(component: T): ComponentId = components[component::class] ?: createComponent(component)

    private fun <T: Component> createComponent(component: T) = ComponentId(components.count()).also { components[component::class] = it }

    private fun column(componentId: ComponentId, archetypeId: ArchetypeId): ArchetypeColumn? = componentArchetype[componentId]?.get(archetypeId)
}

//TODO: naming is inconsistent
data class Archetype(val id: ArchetypeId, val type: List<ComponentId>): Iterable<Row> {
    val edges: MutableMap<ComponentId, ArchetypeId> = mutableMapOf()
    private val table: MutableList<Row> = mutableListOf()

    fun createRow(): RowId = RowId(table.add(mutableListOf()).run{ table.lastIndex }, id)

    fun read(rowId: RowId): Row {
        return table[rowId.id]
    }

    fun deleteRow(rowId: RowId): Row {
        val row = table[rowId.id]
        table[rowId.id] = mutableListOf()
        return row
    }

    fun <T: Component> update(rowId: RowId, column: ArchetypeColumn, component: T): RowId {
        table[rowId.id][column.id] = component
        return rowId
    }

    fun insert(row: Row): RowId {
        val id = createRow()
        table[id.id] = row
        return id
    }

    fun update(rowId: RowId, row: Row) {
        table[rowId.id] = row
    }

    override fun iterator(): Iterator<Row> = table.iterator()
}

fun <T: Comparable<T>, U> List<T>.alignedWith(other: List<U>): List<Pair<T, U>> = zip(other).sortedBy { it.first }

fun List<TypedComponent>.type(): List<ComponentId> = map{it.first}
fun List<TypedComponent>.row(): MutableList<Component> = map{it.second}.toMutableList()

typealias Row = MutableList<Component>
typealias TypedComponent = Pair<ComponentId, Component>
typealias TypedComponentList = List<TypedComponent>