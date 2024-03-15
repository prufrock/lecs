package com.dkanen.lecs

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class WorldTest {
    @Test
    fun testCreateEntity() {
        val world = World()

        val entity = world.createEntity()
        assertEquals(1.eid, entity.id)

        val entity2 = world.createEntity()
        assertEquals(2.eid, entity2.id)
    }
}