package com.dkanen.lecs

import com.dkanen.lecs.component.chart.Query

data class System(val id: Int, private val query: Query, private val world: World, private val block: (Components) -> Unit) {
    fun execute() {
        world.select(query, block)
    }
}
