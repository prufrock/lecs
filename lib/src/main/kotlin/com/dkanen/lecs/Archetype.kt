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
typealias Column = MutableList<Any?> //TODO: Shouldn't this be called a Row?

class MetaArchetype(): Component

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

data class Record(var archetype: Archetype, var row: RowId?) {
    override fun toString(): String {
        return buildString {
            append("Record(archetype=${archetype}, row=$row)")
        }
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
    this.components[row] = this.nullRow()
}

/**
 * Create a row of nulls for this Archetype.
 */
fun Archetype.nullRow(): MutableList<Any?> = this.type.nullRow()

/**
 * Count the number of components in this Archetype.
 */
fun Archetype.countComponents(): Int = components.fold(0) { acc, row -> acc + row.size }

/**
 * Count the number of rows in this Archetype.
 */
fun Archetype.countRows(): Int = components.size


fun Archetype.insert(row: Column): RowId? = components.insert(row)

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

fun <T>MutableList<T?>.fill(size: Int) {
    for (i in count() until size) {
        this.add(null)
    }
}
