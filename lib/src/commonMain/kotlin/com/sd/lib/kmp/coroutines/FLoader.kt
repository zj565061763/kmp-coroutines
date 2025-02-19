package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

interface FLoader {
  /** 状态流 */
  val stateFlow: Flow<State>

  /** 加载状态流 */
  val loadingFlow: Flow<Boolean>

  /** 状态 */
  val state: State

  /** 是否正在加载中 */
  val isLoading: Boolean

  /**
   * 开始加载，如果上一次加载还未完成，再次调用此方法，会取消上一次加载，
   * [onLoad]的异常会被捕获，除了[CancellationException]
   *
   * 注意：[onLoad]中不允许嵌套调用[load]，否则会抛异常
   *
   * @param onLoad 加载回调
   */
  suspend fun <T> load(onLoad: suspend LoadScope.() -> T): Result<T>

  /**
   * 如果正在加载中，会抛出[CancellationException]
   */
  suspend fun <T> tryLoad(onLoad: suspend LoadScope.() -> T): Result<T>

  /** 取消加载，并等待取消完成 */
  suspend fun cancel()

  data class State(
    /** 是否正在加载中 */
    val isLoading: Boolean = false,

    /** 最后一次的加载结果 */
    val result: Result<Unit>? = null,
  )

  interface LoadScope {
    /**
     * onLoad成功或者失败都会触发[block]，触发时所在的协程可能已经被取消
     */
    fun onLoadFinish(block: suspend () -> Unit)
  }
}

fun FLoader(): FLoader = LoaderImpl()

//-------------------- impl --------------------

private class LoaderImpl : FLoader, FLoader.LoadScope {
  private val _mutator = Mutator()
  private val _stateFlow = MutableStateFlow(FLoader.State())
  private var _onFinishBlock: (suspend () -> Unit)? = null

  override val stateFlow: Flow<FLoader.State>
    get() = _stateFlow.asStateFlow()

  override val loadingFlow: Flow<Boolean>
    get() = _stateFlow.map { it.isLoading }.distinctUntilChanged()

  override val state: FLoader.State
    get() = _stateFlow.value

  override val isLoading: Boolean
    get() = state.isLoading

  override suspend fun <T> load(onLoad: suspend FLoader.LoadScope.() -> T): Result<T> {
    return _mutator.mutate {
      doLoad(onLoad)
    }
  }

  override suspend fun <T> tryLoad(onLoad: suspend FLoader.LoadScope.() -> T): Result<T> {
    return _mutator.tryMutate {
      doLoad(onLoad)
    }
  }

  override suspend fun cancel() {
    _mutator.cancelMutate()
  }

  override fun onLoadFinish(block: suspend () -> Unit) {
    _onFinishBlock = block
  }

  private suspend fun <T> Mutator.MutateScope.doLoad(onLoad: suspend FLoader.LoadScope.() -> T): Result<T> {
    return try {
      _stateFlow.update { it.copy(isLoading = true) }
      onLoad().let { data ->
        Result.success(data).also {
          ensureMutateActive()
          _stateFlow.update { it.copy(result = Result.success(Unit)) }
        }
      }
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      Result.failure<T>(e).also {
        ensureMutateActive()
        _stateFlow.update { it.copy(result = Result.failure(e)) }
      }
    } finally {
      _stateFlow.update { it.copy(isLoading = false) }
      _onFinishBlock?.also { finishBlock ->
        _onFinishBlock = null
        finishBlock()
      }
    }
  }
}

private class Mutator {
  private var _job: Job? = null
  private val _jobMutex = Mutex()
  private val _mutateMutex = Mutex()

  suspend fun <R> mutate(block: suspend MutateScope.() -> R): R {
    checkNested()
    return mutate(
      onStart = {},
      block = block,
    )
  }

  suspend fun <T> tryMutate(block: suspend MutateScope.() -> T): T {
    checkNested()
    return mutate(
      onStart = { if (_job?.isActive == true) throw CancellationException() },
      block = block,
    )
  }

  suspend fun cancelMutate() {
    _jobMutex.withLock {
      _job?.cancelAndJoin()
      _job = null
    }
  }

  private suspend fun <R> mutate(
    onStart: () -> Unit,
    block: suspend MutateScope.() -> R,
  ): R {
    return coroutineScope {
      val mutateContext = coroutineContext
      val mutateJob = checkNotNull(mutateContext[Job])

      _jobMutex.withLock {
        onStart()
        _job?.cancelAndJoin()
        _job = mutateJob
        mutateJob.invokeOnCompletion { releaseJob(mutateJob) }
      }

      doMutate {
        with(newMutateScope(mutateContext)) { block() }
      }
    }
  }

  private suspend fun <T> doMutate(block: suspend () -> T): T {
    return _mutateMutex.withLock {
      withContext(MutateElement(mutator = this@Mutator)) {
        block()
      }
    }
  }

  private fun releaseJob(job: Job) {
    if (_jobMutex.tryLock()) {
      if (_job === job) _job = null
      _jobMutex.unlock()
    }
  }

  private fun newMutateScope(mutateContext: CoroutineContext): MutateScope {
    return object : MutateScope {
      override suspend fun ensureMutateActive() {
        currentCoroutineContext().ensureActive()
        mutateContext.ensureActive()
      }
    }
  }

  private suspend fun checkNested() {
    val element = currentCoroutineContext()[MutateElement]
    if (element?.mutator === this@Mutator) error("Nested invoke")
  }

  private class MutateElement(val mutator: Mutator) : AbstractCoroutineContextElement(MutateElement) {
    companion object Key : CoroutineContext.Key<MutateElement>
  }

  interface MutateScope {
    suspend fun ensureMutateActive()
  }
}