package com.angmarx.bounceblade.game.level

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelDefinitionTest {

    @Test
    fun validate_acceptsValidLevel() {
        val level = LevelDefinition(
            id = "level-1",
            name = "First Steps",
            playerSpawn = NormalizedPoint(0.1f, 0.5f),
            bounceLimit = 5,
            objects = listOf(
                LevelObject(
                    id = "switch-a",
                    type = ObjectType.SWITCH,
                    position = NormalizedPoint(0.4f, 0.5f),
                    linkedId = "exit-a"
                ),
                LevelObject(
                    id = "exit-a",
                    type = ObjectType.EXIT,
                    position = NormalizedPoint(0.9f, 0.5f)
                )
            )
        )

        assertTrue(LevelValidator.validate(level).isValid)
    }

    @Test
    fun validate_rejectsSpawnOutsideNormalizedBounds() {
        val level = validLevel().copy(
            playerSpawn = NormalizedPoint(1.2f, 0.5f)
        )

        val result = LevelValidator.validate(level)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Player spawn") })
    }

    @Test
    fun validate_rejectsNonPositiveBounceLimit() {
        val level = validLevel().copy(bounceLimit = 0)

        val result = LevelValidator.validate(level)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Bounce limit") })
    }

    @Test
    fun validate_rejectsSwitchLinkedToMissingExit() {
        val level = validLevel().copy(
            objects = listOf(
                LevelObject(
                    id = "switch-a",
                    type = ObjectType.SWITCH,
                    position = NormalizedPoint(0.4f, 0.5f),
                    linkedId = "missing-exit"
                ),
                LevelObject(
                    id = "exit-a",
                    type = ObjectType.EXIT,
                    position = NormalizedPoint(0.9f, 0.5f)
                )
            )
        )

        val result = LevelValidator.validate(level)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("must link to an existing exit") })
    }

    @Test
    fun validate_rejectsDuplicateObjectIds() {
        val level = validLevel().copy(
            objects = listOf(
                LevelObject(
                    id = "same-id",
                    type = ObjectType.ENEMY,
                    position = NormalizedPoint(0.4f, 0.5f)
                ),
                LevelObject(
                    id = "same-id",
                    type = ObjectType.EXIT,
                    position = NormalizedPoint(0.9f, 0.5f)
                )
            )
        )

        val result = LevelValidator.validate(level)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Object IDs must be unique") })
    }

    @Test
    fun validate_rejectsLevelWithoutExit() {
        val level = validLevel().copy(objects = emptyList())

        val result = LevelValidator.validate(level)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("at least one exit") })
    }

    private fun validLevel() = LevelDefinition(
        id = "level-1",
        name = "First Steps",
        playerSpawn = NormalizedPoint(0.1f, 0.5f),
        bounceLimit = 5,
        objects = listOf(
            LevelObject(
                id = "exit-a",
                type = ObjectType.EXIT,
                position = NormalizedPoint(0.9f, 0.5f)
            )
        )
    )
}
