package com.example

import com.example.ui.AppScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockViewModelNavigationTest {

    class SimpleNavigator {
        private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
        val currentScreen = _currentScreen.asStateFlow()
        private val _screenStack = mutableListOf(AppScreen.DASHBOARD)

        fun navigateTo(screen: AppScreen) {
            if (_screenStack.lastOrNull() != screen) {
                _screenStack.add(screen)
                _currentScreen.value = screen
            }
        }

        fun navigateBack(): Boolean {
            if (_screenStack.size > 1) {
                _screenStack.removeAt(_screenStack.lastIndex)
                _currentScreen.value = _screenStack.last()
                return true
            }
            return false
        }
    }

    @Test
    fun testNavigationFlow() {
        val nav = SimpleNavigator()
        assertEquals(AppScreen.DASHBOARD, nav.currentScreen.value)

        // Navigate to STOCK
        nav.navigateTo(AppScreen.STOCK)
        assertEquals(AppScreen.STOCK, nav.currentScreen.value)

        // Navigate to STOCK_IN_FORM
        nav.navigateTo(AppScreen.STOCK_IN_FORM)
        assertEquals(AppScreen.STOCK_IN_FORM, nav.currentScreen.value)

        // Navigate back
        val backed1 = nav.navigateBack()
        assertTrue(backed1)
        assertEquals(AppScreen.STOCK, nav.currentScreen.value)

        // Navigate back to DASHBOARD
        val backed2 = nav.navigateBack()
        assertTrue(backed2)
        assertEquals(AppScreen.DASHBOARD, nav.currentScreen.value)
    }
}
