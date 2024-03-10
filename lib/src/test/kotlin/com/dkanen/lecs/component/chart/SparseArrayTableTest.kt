package com.dkanen.lecs.component.chart

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SparseArrayTableTest {
    @Test
    fun `create a table without components`() {
        val table = SparseArrayTable(1, emptyList())

        table.create()

        assertEquals(1, table.count)
    }

    @Test
    fun `create a table with a single component`() {
        val table = SparseArrayTable(1, listOf(Lightning::class))

        val i = table.create()

        val row = table.read(i)

        row[0].let {
            val l = it as Lightning
            assertEquals(0, l.pts)
        }

        assertEquals(1, table.count)
    }

    @Test
    fun `create a table with two components`() {
        val table = SparseArrayTable(1, listOf(
            Lightning::class,
            Tablets::class
        ))

        val i = table.create()

        val row = table.read(i)

        row[0].let {
            val l = it as Lightning
            assertEquals(0, l.pts)
        }

        row[1].let {
            val l = it as Tablets
            assertEquals(0, l.count)
        }

        assertEquals(1, table.count)
    }

    @Test
    fun `delete a row`() {
        val table = SparseArrayTable(1, listOf(Lightning::class))

        val i = table.create()

        assertEquals(1, table.count)

        table.delete(i)

        assertEquals(0, table.count)
    }

    @Test
    fun `insert a row`() {
        val table = SparseArrayTable(1, listOf(
            Lightning::class,
            Tablets::class
        ))

        val i = table.insert(listOf(
            Lightning(1),
            Tablets(5)
        ))

        assertEquals(1, table.count)

        val row = table.read(i)

        row[0].let {
            val l = it as Lightning
            assertEquals(1, l.pts)
        }

        row[1].let {
            val l = it as Tablets
            assertEquals(5, l.count)
        }
    }

    @Test
    fun `update a row`() {
        val table = SparseArrayTable(1, listOf(
            Lightning::class,
            Tablets::class
        ))

        val i = table.insert(listOf(
            Lightning(1),
            Tablets(5)
        ))


        assertEquals(1, table.count)

        /**
         * Since it's all pointers don't need to update in this way.
         * (row[0] as Lightning).pts = 2600 does the same, likely also
         * faster since it avoids a reallocation.
         */
        table.update(i, 0, Lightning(2600))

        val row = table.read(i)

        assertEquals(2600, (row[0] as Lightning).pts)
    }
}

data class Lightning(var pts: Int = 0): Component

data class Tablets(var count: Int = 0): Component