package com.dkanen.lecs.component.chart

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FixedComponentChartTest {

    @Test
    fun createRow() {
        val chart = FixedComponentChart()

        val row = chart.createRow()

        assertEquals(0, row.archetypeId.id)
        assertEquals(0, row.id)
    }

    @Test
    fun addComponent() {
        val chart = FixedComponentChart()

        val row = chart.createRow()

        val firstComponentRow = chart.addComponent(row, Health(10))
        assertEquals(1, firstComponentRow.archetypeId.id)

        val readdTheSameComponentRow = chart.addComponent(firstComponentRow, Health(10))
        assertEquals(1, readdTheSameComponentRow.archetypeId.id)

        val addTheSecondComponentRow = chart.addComponent(readdTheSameComponentRow, Mana(1))
        assertEquals(2, addTheSecondComponentRow.archetypeId.id)

        val addTheThirdComponentRow = chart.addComponent(addTheSecondComponentRow, Cakes(4))
        assertEquals(3, addTheThirdComponentRow.archetypeId.id)
    }

    @Test
    fun readComponent() {
        val chart = FixedComponentChart()

        val row = chart.createRow().let {
            chart.addComponent(it, Health(3))
        }

        val component = chart.readComponent(row, Health::class)
        assertEquals(3, component.pts)
    }

    @Test
    fun removeComponent() {
        val chart = FixedComponentChart()

        val row = chart.createRow()

        val componentRow = chart.addComponent(row, Health(10))
        val removeFirstComponent = chart.removeComponent(componentRow, Health::class)
        assertEquals(0, removeFirstComponent.archetypeId.id)

        val firstComponentRow = chart.addComponent(removeFirstComponent, Health(10))
        assertEquals(1, firstComponentRow.archetypeId.id)

        val secondComponentRow = chart.addComponent(firstComponentRow, Mana(1))
        assertEquals(2, secondComponentRow.archetypeId.id)

        val thirdComponentRow = chart.addComponent(secondComponentRow, Cakes(4))
        assertEquals(3, thirdComponentRow.archetypeId.id)

        val removeSecondComponent = chart.removeComponent(thirdComponentRow, Mana::class)
        assertEquals(4, removeSecondComponent.archetypeId.id)

        val putSecondComponentBack = chart.addComponent(removeSecondComponent, Mana(2))
        assertEquals(3, putSecondComponentBack.archetypeId.id)
    }

    @Test
    fun `select a single row by it's only component`() {
        val chart = FixedComponentChart()

        chart.createRow().let {
            chart.addComponent(it, Health(3))
        }
        var count = 0
        val foundComponents = mutableListOf<Component>()
        chart.select(Query(listOf(Health::class))) { components, columns ->
            count++
            foundComponents.add(components[columns[0].id])
        }
        assertEquals(1, count)
        assertEquals(3, (foundComponents.first() as Health).pts)
    }

    @Test
    fun `select a single row by it's second component`() {
        val chart = FixedComponentChart()

        chart.createRow().let {
            val newRow = chart.addComponent(it, Health(3))
            chart.addComponent(newRow, Mana(2))
        }
        var count = 0
        val foundComponents = mutableListOf<Component>()
        chart.select(Query(listOf(Mana::class))) { components, columns ->
            count++
            foundComponents.add(components[columns[0].id])
        }
        assertEquals(1, count)
        assertEquals(2, (foundComponents.first() as Mana).pts)
    }

    @Test
    fun `select three rows by two components`() {
        val chart = FixedComponentChart()

        chart.createRow().let {
            // Is the ergonomics of need to switch to the new row after each change a problem?
            // Should the old row be invalidated somehow so that there's an error if you do something with it?
            val newRow = chart.addComponent(it, Health(3))
            chart.addComponent(newRow, Mana(2))
        }
        chart.createRow().let {
            val newRow = chart.addComponent(it, Health(3))
            chart.addComponent(newRow, Mana(2))
        }
        chart.createRow().let {
            val newRow = chart.addComponent(it, Health(3))
            chart.addComponent(newRow, Mana(2))
        }
        var count = 0
        val foundComponents = mutableListOf<Component>()
        chart.select(Query(listOf(Mana::class, Health::class))) { components, columns ->
            count++
            foundComponents.add(components[columns[0].id])
        }
        assertEquals(3, count)
    }

    @Test
    fun `select from two different groups of components`() {
        val chart = FixedComponentChart()

        chart.createRow().let {
            chart.addComponent(it, Health(3))
        }

        chart.createRow().let {
            chart.addComponent(it, Mana(23))
        }

        var count1 = 0
        val foundComponents1 = mutableListOf<Component>()
        chart.select(Query(listOf(Health::class))) { components, columns ->
            count1++
            foundComponents1.add(components[columns[0].id])
        }
        assertEquals(1, count1)
        assertEquals(3, (foundComponents1.first() as Health).pts)

        var count2 = 0
        val foundComponents2 = mutableListOf<Component>()
        chart.select(Query(listOf(Mana::class))) { components, columns ->
            count2++
            foundComponents2.add(components[columns[0].id])
        }
        assertEquals(1, count2)
        assertEquals(23, (foundComponents2.first() as Mana).pts)
    }

    @Test
    fun `query to update a component on a row with a single component`() {
        val chart = FixedComponentChart()

        val row = chart.createRow().let {
            chart.addComponent(it, Health(3))
        }

        chart.select(Query(listOf(Health::class))) { components, columns ->
            val health = components[columns[0].id] as Health

            health.pts = 5
        }

        val health = chart.readComponent(row, Health::class)
        assertEquals(5, health.pts)
    }

    @Test
    fun `query to update a component on a row with three components`() {
        val chart = FixedComponentChart()

        val row = chart.createRow().let {
            chart.addComponent(it, Health(3))
        }.let {
            chart.addComponent(it, Cakes(24))
        }.let {
            chart.addComponent(it, Mana(7))
        }

        chart.select(Query(listOf(Cakes::class))) { components, columns ->
            val cakes = components[columns[0].id] as Cakes

            cakes.count = 5
        }

        val cakes = chart.readComponent(row, Cakes::class)
        assertEquals(5, cakes.count)
    }
}

data class Health(var pts: Int = 0): Component

data class Mana(var pts: Int = 0): Component

data class Cakes(var count: Int = 0): Component