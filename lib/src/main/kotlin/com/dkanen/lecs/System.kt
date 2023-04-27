package com.dkanen.lecs

typealias SystemId = Int

data class System(val selector: List<ComponentId>, val update: (components: List<Component>) -> Unit): Component

class MetaSystem(): Component