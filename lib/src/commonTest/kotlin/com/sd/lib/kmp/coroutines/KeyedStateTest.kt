package com.sd.lib.kmp.coroutines

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KeyedStateTest : MainDispatcherTest() {

  @Test
  fun `test flow before update`() = runTest {
    val state = FKeyedState { 0 }
    state.flowOf("").test {
      assertEquals(0, awaitItem())
    }
  }

  @Test
  fun `test flow after update`() = runTest {
    val state = FKeyedState { 0 }

    state.update("", 1)
    runCurrent()

    state.flowOf("").test {
      assertEquals(1, awaitItem())
    }
  }

  @Test
  fun `test update multi times`() = runTest {
    val state = FKeyedState { 0 }

    state.update("", 1)
    state.update("", 2)

    state.flowOf("").test {
      assertEquals(2, awaitItem())
    }
  }

  @Test
  fun `test updateAndRelease`() = runTest {
    val state = FKeyedState { 0 }
    val flow = state.flowOf("")

    state.updateAndRelease("", 999)
    runCurrent()
    flow.test {
      assertEquals(0, awaitItem())
    }

    flow.test {
      assertEquals(0, awaitItem())
      state.updateAndRelease("", 1)
      assertEquals(1, awaitItem())
    }

    flow.test {
      assertEquals(0, awaitItem())
    }
  }
}