package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

class RunCatchingTest {
  @Test
  fun `test runCatchingEscape`() = runTest {
    runCatching {
      fRunCatchingEscape {
        throw CancellationException("error")
      }
    }.also { result ->
      assertEquals("error", (result.exceptionOrNull() as CancellationException).message)
    }
  }

  @Test
  fun `test runCatchingEscape escape none default`() = runTest {
    runCatching {
      fRunCatchingEscape(
        escape = { it is IllegalStateException },
      ) {
        error("error")
      }
    }.also { result ->
      assertEquals("error", (result.exceptionOrNull() as IllegalStateException).message)
    }
  }

  @Test
  fun `test runCatchingEscape escape all`() = runTest {
    runCatching {
      fRunCatchingEscape(
        escape = { true },
      ) {
        error("escape all")
      }
    }.also { result ->
      assertEquals("escape all", (result.exceptionOrNull() as IllegalStateException).message)
    }
  }

  @Test
  fun `test runCatchingEscape catch all`() = runTest {
    runCatching {
      fRunCatchingEscape(
        escape = { false },
      ) {
        error("error")
      }
    }.also { result ->
      assertEquals(true, result.isSuccess)
    }
  }
}