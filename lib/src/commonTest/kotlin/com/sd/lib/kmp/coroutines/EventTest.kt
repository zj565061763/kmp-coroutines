package com.sd.lib.kmp.coroutines

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EventTest : MainDispatcherTest() {

  @Test
  fun `test event`() = runTest {
    var count = 0

    val job1 = launch {
      FEvent.flowOf<TestEvent>().collect {
        count++
      }
    }

    val job2 = launch {
      FEvent.flowOf<TestEvent>().collect {
        count++
      }
    }

    runCurrent()

    FEvent.post(TestEvent())
    runCurrent()
    assertEquals(2, count)

    job1.cancelAndJoin()
    FEvent.post(TestEvent())
    runCurrent()
    assertEquals(3, count)

    job2.cancel()
  }

  @Test
  fun `test flow`() = runTest {
    FEvent.flowOf<String>().test {
      FEvent.post("1")
      FEvent.emit("1")
      FEvent.post("1")
      assertEquals("1", awaitItem())
      assertEquals("1", awaitItem())
      assertEquals("1", awaitItem())
    }
  }

  private class TestEvent
}