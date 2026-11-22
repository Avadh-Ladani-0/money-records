package com.moneyrecords.app.ui.components

import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.repository.ComboIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerTilesTest {
    private fun cat(id: Long, name: String, icon: String, parent: Long? = null, children: List<Category> = emptyList()) =
        Category(id = id, name = name, kind = CategoryKind.EXPENSE, parentId = parent, iconKey = icon, children = children)

    @Test fun `combined categories become tiles right after their parent and leave its subcategories`() {
        val homeFood = cat(30, "Home-Food", ComboIcon.key("rent", "food"), parent = 1)
        val snacks = cat(11, "Snacks", "food", parent = 2)
        val tiles = pickerTiles(
            listOf(
                cat(1, "Home", "rent", children = listOf(homeFood)),
                cat(2, "Food", "food", children = listOf(snacks)),
                cat(3, "Fuel", "fuel"),
            ),
        )
        assertEquals(listOf("Home", "Home-Food", "Food", "Fuel"), tiles.map { it.name })
        assertEquals(emptyList<Category>(), tiles[0].children)            // combo no longer a chip under Home
        assertEquals(listOf("Snacks"), tiles[2].children.map { it.name }) // ordinary subcategory kept
    }
}
