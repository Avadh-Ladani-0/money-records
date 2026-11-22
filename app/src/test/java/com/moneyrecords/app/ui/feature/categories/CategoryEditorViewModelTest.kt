package com.moneyrecords.app.ui.feature.categories

import androidx.lifecycle.SavedStateHandle
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.testing.FakeCategories
import com.moneyrecords.app.testing.TestData
import com.moneyrecords.app.ui.navigation.RouteArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditorViewModelTest {
    private lateinit var categories: FakeCategories

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        categories = FakeCategories(TestData.trees)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg args: Pair<String, Any>) = CategoryEditorViewModel(SavedStateHandle(args.toMap()), categories)

    private fun allExpense() = categories.trees.value.getValue(CategoryKind.EXPENSE).flatMap { listOf(it) + it.children }

    @Test fun `combined mode creates a category inside the first, named after both`() {
        val vm = vm(RouteArgs.KIND to "EXPENSE")
        vm.setCombineMode(true)
        assertFalse(vm.state.value.canSave)

        vm.onFirst(TestData.fuel.id)
        vm.onSecond(TestData.food.id)
        assertEquals("Fuel-Food", vm.state.value.name)
        assertTrue(vm.state.value.canSave)

        vm.swap()
        assertEquals(TestData.food.id, vm.state.value.firstId)
        assertEquals("Food-Fuel", vm.state.value.name)

        vm.save()
        val created = allExpense().single { it.name == "Food-Fuel" }
        assertEquals(TestData.food.id, created.parentId)
        assertEquals(ComboIcon.key("food", "fuel"), created.iconKey)
    }

    @Test fun `picking the same category for both clears the other slot`() {
        val vm = vm(RouteArgs.KIND to "EXPENSE")
        vm.setCombineMode(true)
        vm.onFirst(TestData.fuel.id)
        vm.onSecond(TestData.fuel.id)
        assertEquals(null, vm.state.value.firstId)
        assertFalse(vm.state.value.canSave)
    }

    @Test fun `a typed name is kept when the picks change`() {
        val vm = vm(RouteArgs.KIND to "EXPENSE")
        vm.setCombineMode(true)
        vm.onFirst(TestData.fuel.id)
        vm.onName("Commute meals")
        vm.onSecond(TestData.food.id)
        assertEquals("Commute meals", vm.state.value.name)
    }

    @Test fun `editing an existing combined category is detected and keeps its icon and parent`() {
        val vm0 = vm(RouteArgs.KIND to "EXPENSE")
        vm0.setCombineMode(true); vm0.onFirst(TestData.fuel.id); vm0.onSecond(TestData.food.id); vm0.save()
        val combo = allExpense().single { it.name == "Fuel-Food" }

        val vm = vm(RouteArgs.CATEGORY_ID to combo.id)
        val s = vm.state.value
        assertTrue(s.isCombo)
        assertEquals("Fuel", s.comboParentName)
        vm.onName("Road trips")
        vm.save()
        val renamed = allExpense().single { it.id == combo.id }
        assertEquals("Road trips", renamed.name)
        assertEquals(combo.iconKey, renamed.iconKey)
        assertEquals(TestData.fuel.id, renamed.parentId)
    }
}
