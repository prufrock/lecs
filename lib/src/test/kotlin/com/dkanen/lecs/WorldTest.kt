package com.dkanen.lecs

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class WorldTest {
    var world = World()
    var expectedEntityCounter = 0
    var expectedkClassIndexSize = 0
    var expectedComponentIndexSize = 0
    var expectedEntityIndexSize = 0

    fun setUp() {
        world = World()
        expectedEntityCounter = 0
        expectedkClassIndexSize = 0
        expectedComponentIndexSize = 0
        expectedEntityIndexSize = 0
    }

    @Test
    fun addOneComponentToAnEntity() {
        assertEquals(0, world.rootEntity)
        setExpectInitialWorldValues()
        checkCountersAndIndexes()

        val entityId = world.entity()
        expectedEntityCounter += 1
        checkCountersAndIndexes()

        val positionId = world.addComponent(entityId, Position::class)
        assertTrue(world.hasComponent(positionId, MetaComponent::class))
        assertEquals(1, world.entityIndex[entityId]!!.archetype.components.count())
        world.addComponent(entityId, Position::class) // make sure adding the same component twice is idempotent
        assertEquals(1, world.entityIndex[entityId]!!.archetype.components.count())
        expectedEntityCounter += 2 // Add: position[Component], position[Archetype]
        expectedkClassIndexSize += 1 // Add: position[Component]
        expectedComponentIndexSize += 1 // Add: position[Component]
        expectedEntityIndexSize += 3 // Add: entity(now that it has a component), position[Component], position[Archetype]
        checkCountersAndIndexes()
        assertTrue(world.entityIndex[world.rootEntity]!!.archetype.edges[positionId]?.add != null, "Position[Component] should be in the rootEntity's archetype.")
        assertTrue(world.hasComponent(entityId, Position::class))

        assertEquals(4, world.findArchetypes(MetaComponent::class).size) // Components: MetaArchetype, MetaComponent, MetaSystem, Position
        assertEquals(3, world.findArchetypes(MetaArchetype::class).size) // Archetype: (Empty), (MetaArchetype), (Position)
        assertEquals(1, world.findArchetypes(Position::class).size)
        assertTrue(world.hasComponent(world.metaComponentEntity, MetaComponent::class))
        assertTrue(world.hasComponent(world.metaArchetypeEntity, MetaComponent::class))
        assertTrue(world.hasComponent(world.metaArchetypeArchetype, MetaArchetype::class))
    }

    @Test
    fun addTwoComponentsToAnEntity() {
        setExpectInitialWorldValues()
        checkCountersAndIndexes()

        val entityId = world.entity()
        expectedEntityCounter += 1 // add entity
        checkCountersAndIndexes()

        val positionId = world.addComponent(entityId, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 3 // Add: entity(now that it has a component), position[Component], position[Archetype]
        checkCountersAndIndexes()
        assertEquals(0, world.entityIndex[entityId]!!.row)

        val velocityId = world.addComponent(entityId, Velocity::class)
        expectedEntityCounter += 2 // Add: Velocity[Component] and Position,Velocity[Archetype]
        expectedkClassIndexSize += 1 // Add: Velocity[Component]
        expectedComponentIndexSize += 1 // Add: Velocity[Component]
        expectedEntityIndexSize += 2 // Add: Velocity[Component], Velocity[Archetype]
        checkCountersAndIndexes()

        assertTrue(world.hasComponent(entityId, Position::class))
        assertTrue(world.hasComponent(entityId, Velocity::class))
        assertEquals(0, world.entityIndex[entityId]!!.row)

        val archetypes = world.findArchetypes(Position::class)
        assertEquals(1, archetypes.size)
    }

    @Test
    fun addDifferentComponentsToEntities() {
        setExpectInitialWorldValues()
        checkCountersAndIndexes()

        val entityId1 = world.entity()
        expectedEntityCounter += 1 // add entity
        checkCountersAndIndexes()

        world.addComponent(entityId1, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 3 // Add: entity1, Position[Component] because now entityId1 has a component to store in a row, Position[Archetype]
        checkCountersAndIndexes()

        val entityId2 = world.entity()
        expectedEntityCounter += 1 // add entity
        checkCountersAndIndexes()

        world.addComponent(entityId2, Velocity::class)
        expectedEntityCounter += 2 // Add: Velocity[Component], Velocity[Archetype]
        expectedkClassIndexSize += 1 // Add: Velocity[Component]
        expectedComponentIndexSize += 1 // Add: Velocity[Component]
        expectedEntityIndexSize += 3 // Add: entity2, Velocity[Component] because now entity the entityId2 has a component to store in a row, Velocity[Archetype]
        checkCountersAndIndexes()

        assertTrue(world.hasComponent(entityId1, Position::class))
        assertTrue(world.hasComponent(entityId2, Velocity::class))

        val archetypes1 = world.findArchetypes(Position::class)
        assertEquals(1, archetypes1.size)
        val archetypes2 = world.findArchetypes(Velocity::class)
        assertEquals(1, archetypes2.size)
    }

    @Test
    fun addOneComponentToAnEntityAndSetIt() {
        setExpectInitialWorldValues()
        checkCountersAndIndexes()

        val entityId = world.entity()
        expectedEntityCounter += 1 // add entity
        checkCountersAndIndexes()

        world.addComponent(entityId, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], Position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 3 // Add: entity, Position[Archetype], Position[Component] because now entityId1 the entity has a component to store in a row

        world.setComponent(entityId, Position(1.0, 2.0))
        world.setComponent(entityId, Position(1.0, 3.0))
        checkCountersAndIndexes() // setting components doesn't change the counters or the indexes

        val component: Position = world.getComponent(entityId, Position::class) ?: fail("Expected component to be not null")
        assertEquals(1.0, component.x)
        assertEquals(3.0, component.y)
        assertTrue(world.hasComponent(entityId, Position::class))

        val archetypes = world.findArchetypes(Position::class)
        assertEquals(1, archetypes.size)
    }

    @Test
    fun addTwoEntitiesThatShareOneComponentButDifferOnAnotherComponent() {
        setExpectInitialWorldValues()
        checkCountersAndIndexes()

        val player = world.entity()
        expectedEntityCounter += 1 // add entity
        checkCountersAndIndexes()

        val car = world.entity()
        expectedEntityCounter += 1 // add entity
        checkCountersAndIndexes()

        val positionId = world.addComponent(car, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], Position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 3 // Add: car, Position[Component] because now the car entity has a component to store in a row, Position[Archetype]
        checkCountersAndIndexes()

        world.addComponent(player, Position::class)
        expectedEntityCounter += 0 // no change since the existing Position[Component] is being reused
        expectedkClassIndexSize += 0 // no change because Position[MetaComponent] already exists
        expectedComponentIndexSize += 0 // no change because Position[MetaComponent] already exists
        expectedEntityIndexSize += 1 // Add: Position[Component] for the player entity
        checkCountersAndIndexes()
        assertEquals(1, world.componentIndex[positionId]?.count(), "Position[Component] should only 1 archetype.")

        world.addComponent(player, Health::class)
        expectedEntityCounter += 2 // Add: Health[Component] Position,Health[Archetype] for the player entity
        expectedkClassIndexSize += 1 // Add: Health[MetaComponent]
        expectedComponentIndexSize += 1 // Add: Health[MetaComponent]
        expectedEntityIndexSize += 2 // Add: Health[Component], Health[Archetype]
        checkCountersAndIndexes()
    }

    @Test
    fun setTwoComponentsOutOfOrder() {
        val player = world.entity()

        world.addComponent(player, Position::class)
        world.addComponent(player, Velocity::class)
        world.setComponent(player, Velocity(1.0, 2.0))
        world.setComponent(player, Position(3.0, 6.0))

        assertEquals(Velocity(1.0, 2.0), world.getComponent(player, Velocity::class))
    }

    @Test
    fun simpleSystem() {
        val player = world.entity()
        val positionComponent = world.addComponent(player, Position::class)

        world.setComponent(player, Position(1.0, 3.0))

        val systemId = world.addSystem(listOf(positionComponent)) { components ->
            val position: Position = components[0] as Position
            position.x += 1.0
        }

        world.process(systemId)

        assertEquals(2.0, world.getComponent<Position>(player, positionComponent)!!.x)
    }

    private fun setExpectInitialWorldValues() {
        expectedEntityCounter += 7 // rootEntity - 0, emptyArchetype - 1, MetaComponent[Component] - 4, MetaComponent[Archetype] - 5, MetaArchetype[Component] - 2, MetaArchetype[Archetype] - 3, MetaSystem[Component] - 6
        expectedkClassIndexSize += 2 // MetaComponent, MetaArchetype
        expectedComponentIndexSize += 2 // MetaComponent[Component], MetaArchetype[Component]
        expectedEntityIndexSize += 7 // all the entities are now represented in the entity index thanks to meta components
    }

    private fun checkCountersAndIndexes() {
        assertEquals(expectedEntityCounter, world.entityCounter, "expectedEntityCounter: $expectedEntityCounter, world.entityCounter: ${world.entityCounter}")
        assertEquals(expectedkClassIndexSize, world.kClassIndex.size, "expectedkClassIndexSize: $expectedkClassIndexSize, world.kClassIndex.size: ${world.kClassIndex.size}")
        assertEquals(expectedComponentIndexSize, world.componentIndex.size, "expectedComponentIndexSize: $expectedComponentIndexSize, world.componentIndex.size: ${world.componentIndex.size}")
        assertEquals(expectedEntityIndexSize, world.entityIndex.size, "expectedEntityIndexSize: $expectedEntityIndexSize, world.entityIndex.size: ${world.entityIndex.size}")
    }
}