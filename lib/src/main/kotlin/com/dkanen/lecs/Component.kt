package com.dkanen.lecs

typealias ComponentId = EntityId

interface Component

class MetaComponent(): Component

data class Position(var x: Double, val y: Double): Component

data class Velocity(val x: Double, val y: Double): Component

data class Health(val hp: Double): Component