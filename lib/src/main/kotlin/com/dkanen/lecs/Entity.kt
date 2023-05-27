package com.dkanen.lecs

typealias EntityId = Int

data class Entity(var archetype: Archetype, var row: RowId) {
    override fun toString(): String {
        return buildString {
            append("Record(archetype=${archetype}, row=$row)")
        }
    }
}