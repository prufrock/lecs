package com.dkanen.lecs

import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

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

        val entityId = world.createEntity(name = "testEntity")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        val positionId = world.addComponent(entityId, Position::class)
        assertTrue(world.hasComponent(positionId, MetaComponent::class))
        assertEquals(1, world.entityIndex[entityId]!!.archetype.rows.count())
        world.addComponent(entityId, Position::class) // make sure adding the same component twice is idempotent
        assertEquals(1, world.entityIndex[entityId]!!.archetype.rows.count())
        expectedEntityCounter += 2 // Add: position[Component], position[Archetype]
        expectedkClassIndexSize += 1 // Add: position[Component]
        expectedComponentIndexSize += 1 // Add: position[Component]
        expectedEntityIndexSize += 2 // position[Component], position[Archetype]
        checkCountersAndIndexes()
        assertTrue(world.hasComponent(entityId, Position::class))

        assertEquals(6, world.findArchetypes(MetaComponent::class).size) // Components: MetaArchetype, MetaComponent, MetaSystem, Id, Name, Position
        assertEquals(5, world.findArchetypes(MetaArchetype::class).size) // Archetype: (Empty), (MetaArchetype), (Position), (Name), (MetaComponent, Name)
        assertEquals(1, world.findArchetypes(Position::class).size)
        assertTrue(world.hasComponent(world.metaComponentEntity, MetaComponent::class))
        assertTrue(world.hasComponent(world.metaArchetypeEntity, MetaComponent::class))
        assertTrue(world.hasComponent(world.metaArchetypeArchetype, MetaArchetype::class))
    }

    @Test
    fun addOneComponentToTwoDifferentEntities() {
        val first = world.createEntity(name = "entity1")
        val second = world.createEntity(name = "entity2")

        val firstPosition = world.addComponent(first, Position::class)
        world.setComponent(first, Position(1.0, 2.0))

        val secondPosition = world.addComponent(second, Position::class)
        world.setComponent(second, Position(3.0, 4.0))

        assertEquals(1.0, world.getComponent<Position>(first)!!.x)
        assertEquals(3.0, world.getComponent<Position>(second)!!.x)
    }

    @Test
    fun addTwoComponentsToAnEntity() {
        setExpectInitialWorldValues()
        checkCountersAndIndexes()

        val entityId = world.createEntity(name = "entity")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        val positionId = world.addComponent(entityId, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 2 // Add: position[Component], position[Archetype]
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

        val entityId1 = world.createEntity(name = "entity1")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        world.addComponent(entityId1, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 2 // Add:Position[Component] because now entityId1 has a component to store in a row, Position[Archetype]
        checkCountersAndIndexes()

        val entityId2 = world.createEntity(name = "entity2")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        world.addComponent(entityId2, Velocity::class)
        expectedEntityCounter += 2 // Add: Velocity[Component], Velocity[Archetype]
        expectedkClassIndexSize += 1 // Add: Velocity[Component]
        expectedComponentIndexSize += 1 // Add: Velocity[Component]
        expectedEntityIndexSize += 2 // Add: velocity[Component] because now entity the entityId2 has a component to store in a row, Velocity[Archetype]
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

        val entityId = world.createEntity(name = "testEntity")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        world.addComponent(entityId, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], Position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 2 // Add: Position[Archetype], Position[Component] because now entityId1 the entity has a component to store in a row

        world.setComponent(entityId, Position(1.0, 2.0))
        world.setComponent(entityId, Position(1.0, 3.0))
        checkCountersAndIndexes() // setting components doesn't change the counters or the indexes

        val component: Position = world.getComponent<Position>(entityId) ?: fail("Expected component to be not null")
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

        val player = world.createEntity(name = "player")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        val car = world.createEntity(name = "car")
        expectedEntityCounter += 1
        expectedEntityIndexSize += 1
        checkCountersAndIndexes()

        val positionId = world.addComponent(car, Position::class)
        expectedEntityCounter += 2 // Add: Position[Component], Position[Archetype]
        expectedkClassIndexSize += 1 // Add: Position[Component]
        expectedComponentIndexSize += 1 // Add: Position[Component]
        expectedEntityIndexSize += 2 // Add: Position[Component] because now the car entity has a component to store in a row, Position[Archetype]
        checkCountersAndIndexes()

        world.addComponent(player, Position::class)
        expectedEntityCounter += 0 // no change since the existing Position[Component] is being reused
        expectedkClassIndexSize += 0 // no change because Position[MetaComponent] already exists
        expectedComponentIndexSize += 0 // no change because Position[MetaComponent] already exists
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
        val player = world.createEntity(name = "player")

        world.setComponent(player, Velocity(1.0, 2.0))
        world.setComponent(player, Position(3.0, 6.0))

        assertEquals(Velocity(1.0, 2.0), world.getComponent<Velocity>(player))
    }

    @Test
    fun simpleSystem() {
        val player = world.createEntity(name = "player")

        world.setComponent(player, Name("player"))
        world.setComponent(player, Id(player))
        world.setComponent(player, Position(1.0, 3.0))

        var selectedId = 0;
        val systemId = world.addSystem(name = "position system", world.componentToId(Id::class, Position::class)) { components ->
            selectedId = (components[0] as Id).id
            val position: Position = components[1] as Position
            position.x += 1.0
        }

        world.process(systemId)

        assertEquals(player, selectedId)
        assertEquals(2.0, world.getComponent<Position>(player)!!.x)
    }

    @Test
    fun ensureComponentsAreOrderedInArchetypes() {
        val enemy = world.createEntity(name = "enemy")
        world.addComponent(enemy, Position::class)
        world.addComponent(enemy, Velocity::class)

        val player = world.createEntity(name = "player")

        // Set the components in a different order than they were created to ensure the Archetype has to re-order them.
        val velocityComponent = world.addComponent(player, Velocity::class)
        val positionComponent = world.addComponent(player, Position::class)

        val archetype = world.archetypeFor(player)

        assertEquals(world.archetypeFor(enemy), world.archetypeFor(player), "The two entities should be in the same archetype.")
        assertEquals(3, archetype?.type?.count(), "The archetype should have 2 components.")
        assertEquals(positionComponent, archetype?.type?.get(1), "The archetype should have the Position component first.")
        assertEquals(velocityComponent, archetype?.type?.get(2), "The archetype should have the Velocity component second.")
        assertEquals(6, archetype?.countComponents(), "The archetype should have 6 components.")
        assertEquals(2, archetype?.countRows(), "The archetype should have 2 rows.")
    }

    /**
     * Not vectorizing yet, do I need to use Arrays?
     */
    fun willItVectorize() {
        val size = 1000000
        val list = mutableListOf<Position>()

        // fill a list with a million random integers
        for (i in 0..size) {
            list.add(Position(Random.nextDouble(), Random.nextDouble()))
        }

        // start a timer
        var start = measureTimeMillis {
            // add a random integer to each element in the list
            for (i in 0..size) {
                list[i].x += Random.nextDouble()
            }
        }
        println("Time taken: $start")

        var positionComponent: ComponentId? = null
        for (i in 0..size) {
            val entity = world.createEntity(name = "entity-$i")
            if (positionComponent == null) {
                positionComponent = world.addComponent(entity, Position::class)
            }
            world.setComponent(entity, Position(Random.nextDouble(), Random.nextDouble()))
        }

        val systemId = world.addSystem(name = "position system", listOf(positionComponent!!)) { components ->
            val position: Position = components[0] as Position
            position.x += Random.nextDouble()
        }
        world.process(systemId)
        println("done")
    }

    private fun setExpectInitialWorldValues() {
        expectedEntityCounter += 11 // rootEntity - 0, emptyArchetype - 1, MetaComponent[Component] - 4, MetaComponent[Archetype] - 5, MetaArchetype[Component] - 2, MetaArchetype[Archetype] - 3, MetaSystem[Component] - 6, Id[Component] - 7, Name[Component] - 8, Name[Archetype] - 9, MetaComponent, Name[Archetype] - 10
        expectedkClassIndexSize += 4 // MetaComponent, MetaArchetype, Id, Name
        expectedComponentIndexSize += 3 // MetaComponent[Component], MetaArchetype[Component], Name[Component]
        expectedEntityIndexSize += 11 // all the entities are now represented in the entity index thanks to meta components
    }

    private fun checkCountersAndIndexes() {
        assertEquals(expectedEntityCounter, world.entityCounter, "expectedEntityCounter: $expectedEntityCounter, world.entityCounter: ${world.entityCounter}")
        assertEquals(expectedkClassIndexSize, world.kClassIndex.size, "expectedkClassIndexSize: $expectedkClassIndexSize, world.kClassIndex.size: ${world.kClassIndex.size}")
        assertEquals(expectedComponentIndexSize, world.componentIndex.size, "expectedComponentIndexSize: $expectedComponentIndexSize, world.componentIndex.size: ${world.componentIndex.size}")
        assertEquals(expectedEntityIndexSize, world.entityIndex.size, "expectedEntityIndexSize: $expectedEntityIndexSize, world.entityIndex.size: ${world.entityIndex.size}")
    }
}