package com.sd.lib.kmp.coroutines

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LoaderTest : MainDispatcherTest() {

  @Test
  fun `test load when success`() = runTest {
    val loader = FLoader()
    assertEquals(null, loader.state.result)
    loader.load {
      1
    }.also { result ->
      assertEquals(true, loader.state.result!!.isSuccess)
      assertEquals(1, result.getOrThrow())
    }
  }

  @Test
  fun `test load when error in block`() = runTest {
    val loader = FLoader()
    loader.load {
      error("error in block")
    }.also { result ->
      assertEquals("error in block", result.exceptionOrNull()!!.message)
      assertEquals("error in block", loader.state.result!!.exceptionOrNull()!!.message)
    }
  }

  @Test
  fun `test load when loading`() = runTest {
    val loader = FLoader()

    val job = launch {
      loader.load { delay(Long.MAX_VALUE) }
    }.also {
      runCurrent()
    }

    loader.load {
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
      2
    }.also { result ->
      assertEquals(2, result.getOrThrow())
    }
  }

  @Test
  fun `test load when cancel`() = runTest {
    val loader = FLoader()
    launch {
      loader.load { delay(Long.MAX_VALUE) }
    }.also { job ->
      runCurrent()
      loader.cancel()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test load when throw CancellationException in block`() = runTest {
    val loader = FLoader()
    launch {
      loader.load { throw CancellationException() }
    }.also { job ->
      runCurrent()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test load when cancel in block`() = runTest {
    val loader = FLoader()
    launch {
      loader.load { currentCoroutineContext().cancel() }
    }.also { job ->
      runCurrent()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test loadingFlow when params true`() = runTest {
    val loader = FLoader()
    loader.loadingFlow.test {
      loader.load(notifyLoading = true) {}
      assertEquals(false, awaitItem())
      assertEquals(true, awaitItem())
      assertEquals(false, awaitItem())
    }
  }

  @Test
  fun `test loadingFlow when params false`() = runTest {
    val loader = FLoader()
    loader.loadingFlow.test {
      loader.load(notifyLoading = false) {}
      assertEquals(false, awaitItem())
    }
  }

  @Test
  fun `test loadingFlow when Reload`() = runTest {
    val loader = FLoader()
    loader.loadingFlow.test {
      launch {
        loader.load { delay(Long.MAX_VALUE) }
      }.also {
        runCurrent()
        loader.load { }
      }
      assertEquals(false, awaitItem())
      assertEquals(true, awaitItem())
      assertEquals(false, awaitItem())
      assertEquals(true, awaitItem())
      assertEquals(false, awaitItem())
    }
  }

  @Test
  fun `test loadingFlow when cancel`() = runTest {
    val loader = FLoader()
    loader.loadingFlow.test {
      launch {
        loader.load { delay(Long.MAX_VALUE) }
      }.also {
        runCurrent()
        loader.cancel()
      }
      assertEquals(false, awaitItem())
      assertEquals(true, awaitItem())
      assertEquals(false, awaitItem())
    }
  }

  @Test
  fun `test tryLoad`() = runTest {
    val loader = FLoader()

    val job = launch {
      loader.load { delay(Long.MAX_VALUE) }
    }.also {
      runCurrent()
    }

    runCatching {
      loader.tryLoad { 1 }
    }.also { result ->
      assertEquals(true, result.exceptionOrNull() is CancellationException)
    }

    job.cancelAndJoin()
  }

  @Test
  fun `test nested load`() = runTest {
    val loader = FLoader()
    val list = mutableListOf<String>()

    loader.load {
      runCatching {
        loader.load { }
      }.also {
        assertEquals("Already in Mutate", it.exceptionOrNull()!!.message)
        list.add("1")
      }
      list.add("2")
    }

    assertEquals(listOf("1", "2"), list)
  }
}