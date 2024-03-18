package com.dkanen.lecs

import com.dkanen.lecs.component.chart.Component
import com.dkanen.lecs.component.chart.queryOf
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class WorldTest {
    @Test
    fun createEntity() {
        val world = World()

        val entity = world.createEntity()
        assertEquals(1.eid, entity.id)

        val entity2 = world.createEntity()
        assertEquals(2.eid, entity2.id)
    }

    @Test
    fun addComponent() {
        val world = World()

        val entity = world.createEntity()
        entity.addComponent(Punches(32))

        var count = 0

        world.select(queryOf(Punches::class)) { components ->
            count++
            val punches = components.get(Punches::class)
            punches.pow = 24
        }

        assertEquals(1, count)
        assertEquals(24, entity.getComponent(Punches::class).pow)
    }

    @Test
    fun createSystem() {
        val world = World()

        val entity = world.createEntity()
        entity.addComponent(Punches(32))

        var count = 0

        val system = world.createSystem(queryOf(Punches::class)) { components ->
            count++
            val punches = components.get(Punches::class)
            punches.pow = 22
        }

        system.execute()

        assertEquals(1, count)
        assertEquals(22, entity.getComponent(Punches::class).pow)
    }
}

data class Punches(var pow: Int = 0): Component