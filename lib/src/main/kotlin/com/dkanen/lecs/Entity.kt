package com.dkanen.lecs

typealias EntityId = Int

data class Entity(var archetype: Archetype, var row: RowId) {

    /**
     * Find the index of a component in the archetype.
     */
    fun indexOfComponent(component: ComponentId): Int {
        return archetype.indexOfComponent(component)
    }

    override fun toString(): String {
        return buildString {
            append("Record(archetype=${archetype}, row=$row)")
        }
    }
}