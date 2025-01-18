package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MutatorTest {
  @Test
  fun `test mutate success`() = runTest {
    FMutator().mutate { "1" }.also { result ->
      assertEquals("1", result)
    }
  }

  @Test
  fun `test mutate error`() = runTest {
    runCatching {
      FMutator().mutate { error("error") }
    }.also { result ->
      assertEquals("error", result.exceptionOrNull()!!.message)
    }
  }

  @Test
  fun `test mutate CancellationException in block`() = runTest {
    launch {
      FMutator().mutate { throw CancellationException() }
    }.also { job ->
      advanceUntilIdle()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test mutate cancel in block`() = runTest {
    launch {
      FMutator().mutate { cancel() }
    }.also { job ->
      advanceUntilIdle()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test mutate cancelMutate in block`() = runTest {
    val mutator = FMutator()
    launch {
      mutator.mutate { mutator.cancelMutate() }
    }.also { job ->
      advanceUntilIdle()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test cancelMutate when mutate in progress`() = runTest {
    val mutator = FMutator()

    val job = launch {
      mutator.mutate { delay(Long.MAX_VALUE) }
    }.also {
      runCurrent()
      assertEquals(true, mutator.isMutating())
    }

    mutator.cancelMutate()

    assertEquals(false, mutator.isMutating())
    assertEquals(true, job.isCancelled)
    assertEquals(true, job.isCompleted)
  }

  @Test
  fun `test mutate when mutate in progress`() = runTest {
    val mutator = FMutator()

    val job = launch {
      mutator.mutate { delay(Long.MAX_VALUE) }
    }.also {
      runCurrent()
      assertEquals(true, mutator.isMutating())
    }

    mutator.mutate { }

    assertEquals(false, mutator.isMutating())
    assertEquals(true, job.isCancelled)
    assertEquals(true, job.isCompleted)
  }

  @Test
  fun `test mutate when effect in progress`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    val job = launch {
      mutator.effect {
        delay(5_000)
        list.add("1")
      }
    }.also {
      runCurrent()
    }

    mutator.mutate { list.add("2") }
    assertEquals(false, job.isCancelled)
    assertEquals(true, job.isCompleted)
    assertEquals(listOf("1", "2"), list)
  }

  //-------------------- effect --------------------

  @Test
  fun `test effect success`() = runTest {
    FMutator().effect { "1" }.also { result ->
      assertEquals("1", result)
    }
  }

  @Test
  fun `test effect error`() = runTest {
    runCatching {
      FMutator().effect { error("error") }
    }.also { result ->
      assertEquals("error", result.exceptionOrNull()!!.message)
    }
  }

  @Test
  fun `test effect CancellationException in block`() = runTest {
    launch {
      FMutator().effect { throw CancellationException() }
    }.also { job ->
      advanceUntilIdle()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test effect cancel in block`() = runTest {
    launch {
      FMutator().effect { cancel() }
    }.also { job ->
      advanceUntilIdle()
      assertEquals(true, job.isCancelled)
      assertEquals(true, job.isCompleted)
    }
  }

  @Test
  fun `test cancelMutate when effect in progress`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    val job = launch {
      mutator.effect {
        delay(5_000)
        list.add("1")
      }
    }.also {
      runCurrent()
    }

    mutator.cancelMutate()
    assertEquals(false, job.isCancelled)
    assertEquals(false, job.isCompleted)

    advanceUntilIdle()
    assertEquals(false, job.isCancelled)
    assertEquals(true, job.isCompleted)
    assertEquals(listOf("1"), list)
  }

  @Test
  fun `test effect when effect in progress`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    val job = launch {
      mutator.effect {
        delay(5_000)
        list.add("1")
      }
    }.also {
      runCurrent()
    }

    mutator.effect { list.add("2") }
    assertEquals(false, job.isCancelled)
    assertEquals(true, job.isCompleted)
    assertEquals(listOf("1", "2"), list)
  }

  @Test
  fun `test effect when mutate in progress`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    val job = launch {
      mutator.mutate {
        delay(5_000)
        list.add("1")
      }
    }.also {
      runCurrent()
    }

    mutator.effect { list.add("2") }
    assertEquals(false, job.isCancelled)
    assertEquals(true, job.isCompleted)
    assertEquals(listOf("1", "2"), list)
  }

  //-------------------- nested --------------------

  @Test
  fun `test mutate in mutate block`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    mutator.mutate {
      runCatching {
        mutator.mutate { }
      }.also {
        assertEquals("Already in Mutate", it.exceptionOrNull()!!.message)
        list.add("1")
      }
      list.add("2")
    }

    assertEquals(listOf("1", "2"), list)
  }

  @Test
  fun `test effect in effect block`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    mutator.effect {
      runCatching {
        mutator.effect { }
      }.also {
        assertEquals("Already in Effect", it.exceptionOrNull()!!.message)
        list.add("1")
      }
      list.add("2")
    }

    assertEquals(listOf("1", "2"), list)
  }

  @Test
  fun `test mutate in effect block`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    mutator.effect {
      runCatching {
        mutator.mutate { }
      }.also {
        assertEquals("Already in Effect", it.exceptionOrNull()!!.message)
        list.add("1")
      }
      list.add("2")
    }

    assertEquals(listOf("1", "2"), list)
  }

  @Test
  fun `test effect in mutate block`() = runTest {
    val mutator = FMutator()
    val list = mutableListOf<String>()

    mutator.mutate {
      runCatching {
        mutator.effect { }
      }.also {
        assertEquals("Already in Mutate", it.exceptionOrNull()!!.message)
        list.add("1")
      }
      list.add("2")
    }

    assertEquals(listOf("1", "2"), list)
  }
}