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
typealias Column = MutableList<Any>

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
data class Record(var archetype: Archetype, var row: Int?) {
    override fun toString(): String {
        return buildString {
            append("Record(archetype=${archetype.id}, row=$row)")
        }
    }
}
data class ArchetypeRecord(val column: Int)
typealias ArchetypeMap = MutableMap<ArchetypeId, ArchetypeRecord>