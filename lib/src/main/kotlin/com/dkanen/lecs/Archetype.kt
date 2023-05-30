package com.dkanen.lecs

typealias ArchetypeId = EntityId

typealias Type = MutableList<ComponentId>

/**
 * In a language stricter about the size of array elements like Swift, c++ you might need this:
struct Column {
void *elements;      // buffer with component data
size_t element_size; // size of a single element
size_t count;        // number of elements
}
 */
typealias Row = MutableList<Any?> //TODO: Shouldn't this be called a Row?

class MetaArchetype(): Component

data class ArchetypeEdge(
    var add: Archetype? = null,
    var remove: Archetype? = null
)

data class Archetype(
    val id: ArchetypeId,
    val type: Type,
    val rows: MutableList<Row>,
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
        append("Archetype(id=$id, type=$type, components=$rows")
    }
}

typealias RowId = Int

data class ArchetypeRecord(val column: Int)
typealias ArchetypeMap = MutableMap<ArchetypeId, ArchetypeRecord>

/**
 * Remove the row from the Archetype.
 */
fun Archetype.remove(row: RowId) = this.nullRow(row)

/**
 * Fill the row with nulls.
 */
fun Archetype.nullRow(row: RowId) {
    this.rows[row] = this.nullRow()
}

/**
 * Create a row of nulls for this Archetype.
 */
fun Archetype.nullRow(): MutableList<Any?> = this.type.nullRow()

fun Archetype.createEmptyRow(): RowId {
    val row = this.nullRow()
    return this.insertOrFail(row)
}

/**
 * Count the number of components in this Archetype.
 */
fun Archetype.countComponents(): Int = rows.fold(0) { acc, row -> acc + row.size }

/**
 * Count the number of rows in this Archetype.
 */
fun Archetype.countRows(): Int = rows.size


fun Archetype.insertOrFail(row: Row): RowId = rows.insert(row) ?: throw EntityAllocationException("Failed to create entity in archetype ${type} row: ${countRows()} components ${countComponents()}. More than likely the archetype is full.")

fun Archetype.rowAtOrFail(rowId: RowId): Row = rows.elementAtOrNull(rowId) ?: throw EntityAllocationException("Failed to find row $rowId in archetype ${type} row: ${countRows()}. Was the row added or did it get removed?")

/**
 * Find the index of a component in this Archetype.
 */
fun Archetype.indexOfComponent(componentId: ComponentId): Int = type.indexOf(componentId)

/**
 * Create a row of nulls for this Type.
 */
fun Type.nullRow(): MutableList<Any?> = MutableList(this.size) { null }

fun <T>MutableList<T>.insert(element: T): Int? {
    val insertedAt = lastIndex + 1
    return if (this.add(element)) {
        insertedAt
    } else {
        null
    }
}

fun <T>MutableList<T?>.ofNulls(size: Int) {
    null
    for (i in count() until size) {
        this.add(null)
    }
}

class EntityAllocationException(message: String): Exception(message)

fun MutableMap<ComponentId, ArchetypeMap>.putIfAbsent(componentId: ComponentId): ArchetypeMap {
    return this.getOrPut(componentId) { mutableMapOf() }
}