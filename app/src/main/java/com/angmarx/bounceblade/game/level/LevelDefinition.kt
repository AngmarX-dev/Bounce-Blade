package com.angmarx.bounceblade.game.level

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class LevelObject(
    val id: String,
    val type: ObjectType,
    val position: NormalizedPoint,
    val radius: Float = 0.04f,
    val linkedId: String? = null
)

enum class ObjectType {
    ENEMY,
    CRATE,
    SWITCH,
    EXIT,
    HAZARD
}

data class LevelDefinition(
    val id: String,
    val name: String,
    val playerSpawn: NormalizedPoint,
    val bounceLimit: Int,
    val objects: List<LevelObject>
)

data class LevelValidationResult(
    val errors: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

object LevelValidator {

    fun validate(level: LevelDefinition): LevelValidationResult {
        val errors = mutableListOf<String>()

        if (level.id.isBlank()) errors += "Level ID must not be blank"
        if (level.name.isBlank()) errors += "Level name must not be blank"
        if (level.bounceLimit <= 0) errors += "Bounce limit must be positive"

        validatePoint(level.playerSpawn, "Player spawn", errors)

        val duplicateIds = level.objects
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        if (duplicateIds.isNotEmpty()) {
            errors += "Object IDs must be unique: ${duplicateIds.joinToString()}"
        }

        level.objects.forEach { obj ->
            if (obj.id.isBlank()) errors += "Object ID must not be blank"
            validatePoint(obj.position, "Object '${obj.id}' position", errors)

            if (obj.radius <= 0f) {
                errors += "Object '${obj.id}' radius must be positive"
            }

            if (obj.type == ObjectType.SWITCH) {
                val linkedExit = obj.linkedId?.let { id ->
                    level.objects.any { it.id == id && it.type == ObjectType.EXIT }
                } ?: false

                if (!linkedExit) {
                    errors += "Switch '${obj.id}' must link to an existing exit"
                }
            }
        }

        if (level.objects.none { it.type == ObjectType.EXIT }) {
            errors += "Level must contain at least one exit"
        }

        return LevelValidationResult(errors)
    }

    private fun validatePoint(
        point: NormalizedPoint,
        label: String,
        errors: MutableList<String>
    ) {
        if (!point.x.isFinite() || !point.y.isFinite()) {
            errors += "$label coordinates must be finite"
        } else if (point.x !in 0f..1f || point.y !in 0f..1f) {
            errors += "$label coordinates must be between 0 and 1"
        }
    }
}
