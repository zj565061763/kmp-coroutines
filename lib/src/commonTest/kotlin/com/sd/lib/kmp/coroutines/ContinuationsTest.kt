package com.sd.lib.kmp.coroutines

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ContinuationsTest : MainDispatcherTest() {

  @Test
  fun `test resumeAll`() = runTest {
    val continuations = FContinuations<Int>()
    val flow = MutableSharedFlow<Any?>()
    flow.test {
      repeat(3) {
        launch {
          val result = continuations.await()
          flow.emit(result)
        }
      }

      runCurrent()
      continuations.resumeAll(1)
      continuations.resumeAll(2)

      repeat(3) {
        assertEquals(1, awaitItem())
      }
    }
  }

  @Test
  fun `test resumeAllWithException`() = runTest {
    val continuations = FContinuations<Int>()
    val flow = MutableSharedFlow<Any?>()
    flow.test {
      repeat(3) {
        launch {
          try {
            continuations.await()
            flow.emit(1)
          } catch (e: Throwable) {
            flow.emit(e)
          }
        }
      }

      runCurrent()
      continuations.resumeAllWithException(IllegalArgumentException("resumeAllWithException 1"))
      continuations.resumeAllWithException(IllegalStateException("resumeAllWithException 2"))

      repeat(3) {
        assertEquals("resumeAllWithException 1", (awaitItem() as IllegalArgumentException).message)
      }
    }
  }

  @Test
  fun `test cancelAll`() = runTest {
    val continuations = FContinuations<Int>()
    val flow = MutableSharedFlow<Any?>()
    flow.test {
      repeat(3) {
        launch {
          try {
            continuations.await()
            flow.emit(1)
          } catch (e: Throwable) {
            flow.emit(e)
          }
        }
      }

      runCurrent()
      continuations.cancelAll()
      continuations.cancelAll()

      repeat(3) {
        assertEquals(true, awaitItem() is CancellationException)
      }
    }
  }

  @Test
  fun `test cancelAll with cause`() = runTest {
    val continuations = FContinuations<Int>()
    val flow = MutableSharedFlow<Any?>()
    flow.test {
      repeat(3) {
        launch {
          try {
            continuations.await()
            flow.emit(1)
          } catch (e: Throwable) {
            flow.emit(e)
          }
        }
      }

      runCurrent()
      continuations.cancelAll(IllegalArgumentException("cancelAll with cause 1"))
      continuations.cancelAll(IllegalStateException("cancelAll with cause 2"))

      repeat(3) {
        assertEquals("cancelAll with cause 1", (awaitItem() as IllegalArgumentException).message)
      }
    }
  }

  @Test
  fun `test cancel outside`() = runTest {
    val scope = MainScope()

    val continuations = FContinuations<Int>()
    val flow = MutableSharedFlow<Any?>()
    flow.test {
      repeat(3) {
        scope.launch {
          try {
            continuations.await()
            flow.emit(1)
          } catch (e: Throwable) {
            withContext(NonCancellable) {
              flow.emit(e)
            }
          }
        }
      }

      runCurrent()
      scope.cancel()

      repeat(3) {
        assertEquals(true, awaitItem() is CancellationException)
      }
    }
  }
}