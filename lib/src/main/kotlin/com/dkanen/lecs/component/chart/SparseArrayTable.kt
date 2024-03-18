package com.dkanen.lecs.component.chart

import kotlin.reflect.KClass


//TODO: What if adding a new component to an entity meant that the new component was copied to an existing unused component? Would that improve memory locality?
interface Table: Iterable<Row> {

    /**
     * The number of items in the table.
     */
    val count: Int

    /**
     * The maximum number of elements the table can hold.
     */
    val size: Int

    val components: List<KClass<out Component>>

    fun create(): Int
    fun insert(components: List<Component>): Int
    fun read(i: Int): List<Component>
    fun update(i: Int, column: Int, component: Component): List<Component>

    fun update(i: Int, components: List<Component>): List<Component>
    fun delete(i: Int): List<Component>
    fun exists(i: Int): Boolean
}

/**
 * Sparse because when items are deleted no attempt is made to rearrange existing items to fill the gaps.
 * TODO: test exceptions
 */
class SparseArrayTable(override val size: Int, override val components: List<KClass<out Component>>) : Table{

    var index: Int = 0
        private set

    override val count: Int
        get() = index - deleted.size

    private val deleted: MutableSet<Int> = HashSet()

    private val items: Array<Array<Component>> = Array(size) {
        Array(components.size) { componentIndex ->
            components[componentIndex].java.getDeclaredConstructor().newInstance()
        }
    }

    override fun create(): Int = nextRow()

    override fun insert(components: List<Component>): Int {
        val i = nextRow()

        items[i] = components.toTypedArray()

        return i
    }

    override fun read(i: Int): List<Component> {
        throwIfRowDoesNotExist(i)

        return items[i].toList()
    }

    override fun update(i: Int, column: Int, component: Component): List<Component> {
        throwIfRowDoesNotExist(i)

        items[i][column] = component

        return items[i].toList()
    }

    override fun update(i: Int, components: List<Component>): List<Component> {
        throwIfRowDoesNotExist(i)

        items[i] = components.toTypedArray()

        return items[i].toList()
    }

    override fun delete(i: Int): List<Component>  {
        throwIfRowDoesNotExist(i)

        deleted.add(i)

        return items[i].toList()
    }

    override fun iterator(): Iterator<MutableList<Component>> = SparseArrayTableIterator(this)

    override fun exists(i: Int): Boolean {
        return i < index && !deleted.contains(i)
    }

    override fun toString(): String {
        val componentNames = List(components.size) { i ->
            items[0][i]::class.java
        }
        return "Table(${componentNames.size}): $componentNames"
    }

    private fun nextRow(): Int {
        if (deleted.isNotEmpty()) {
            return deleted.removeFirst()
        }
        if (index >= size) {
            throw TableFull("""
                Table is full. Someone attempted to added another row to the table, but it couldn't be added because it's full.
                $this
                Was something supposed to be deleted or is something adding more items than expected?
                """)
        }
        return index++
    }

    private fun throwIfRowDoesNotExist(i: Int) {
        if (i > index) {
            throw RowNotFound("""
                Row $i couldn't be found in the table.
                $this
                Did the row get created?
                """)
        }
        if (deleted.contains(i)) {
            throw RowNotFound("""
                Row $i couldn't be found in the table. It has been deleted.
                $this
                Should it have been deleted?
                """)
        }
    }
}

class SparseArrayTableIterator(private val table: SparseArrayTable): Iterator<MutableList<Component>> {
    private var currentIndex = 0

    override fun hasNext(): Boolean {
        // mind the gaps
        while(currentIndex < table.index && !table.exists(currentIndex)) {
            currentIndex += 1
        }
        return currentIndex < table.index
    }

    override fun next(): MutableList<Component> {
        val i = nextIndex()
        return table.read(i).toMutableList()
    }

    private fun nextIndex(): Int {
        return currentIndex++
    }
}

class RowNotFound(message: String): Exception(message)

class TableFull(message: String): Exception(message)

fun <T> MutableSet<T>.removeFirst(): T {
    val i = first()
    remove(i)
    return i
}
