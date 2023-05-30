package com.dkanen.lecs

class EntityNotFoundException(entityId: EntityId) : Exception("Entity $entityId not found. It may not have been created yet.") {

}
