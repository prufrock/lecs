package com.dkanen.lecs

typealias EntityId = Int

/**
 * You may think a Record is practically an entity, but it's not. An entity is a collection of components.
 * In this way the Record points to an entity but isn't an entity itself.
 *
 * The row points to the row in the archetype that contains the components for the entity.
 */
data class Record(var archetype: Archetype, var row: RowId) {

    /**
     * Find the index of a component in the archetype.
     */
    fun indexOfComponent(component: ComponentId): Int {
        return archetype.indexOfComponent(component)
    }

    override fun toString(): String {
        return buildString {
            append("R(archetype=${archetype}, row=$row)")
        }
    }
}