package com.dkanen.lecs

import com.dkanen.lecs.component.chart.Component
import kotlin.reflect.KClass

class ComponentNotFoundException(type: String) : Throwable(
    """
        The type: [$type} couldn't be found in the result returned when selecting from the World.
        Did you make sure to add it to the Query? If it's there and still not working you may have found a bug.
        If you don't mind taking the time to tell us about it, we'd sure appreciate it. Thanks!
    """.trimIndent()
) {

}
