package com.angmarx.bounceblade.game.editor

import com.angmarx.bounceblade.game.model.NormalizedPoint

/** Editable in-memory data model for the future in-game level editor. */
data class LevelDraft(
    var name: String = "Untitled Level",
    var maxBounces: Int = 5,
    val enemies: MutableList<NormalizedPoint> = mutableListOf(),
    val crates: MutableList<NormalizedPoint> = mutableListOf(),
    val hazards: MutableList<NormalizedPoint> = mutableListOf()
) {
    fun isValid(): Boolean = name.isNotBlank() && maxBounces > 0
}
