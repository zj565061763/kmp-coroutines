package com.sd.lib.kmp.coroutines

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalLaunchTest : MainDispatcherTest() {

  @Test
  fun test() = runTest {
    val flow = MutableSharedFlow<Any?>()
    flow.test {
      fGlobalLaunch {
        flow.emit(1)
      }.also {
        assertEquals(1, awaitItem())
      }

      fGlobalLaunch {
        throw CancellationException()
      }.also {
        runCurrent()
      }

      fGlobalLaunch {
        flow.emit(2)
      }.also {
        assertEquals(2, awaitItem())
      }

      fGlobalLaunch {
        currentCoroutineContext().cancel()
      }.also {
        runCurrent()
      }

      fGlobalLaunch {
        flow.emit(3)
      }.also {
        assertEquals(3, awaitItem())
      }
    }
  }
}