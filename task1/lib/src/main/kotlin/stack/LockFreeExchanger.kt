package stack

import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.DurationUnit

internal object TimeoutError

private sealed class ExchangeState<out T> {
    inline fun runBusy(f: (T) -> Unit) {
        when (this) {
            is Busy -> f(this.value)
            is Waiting -> {}
            Empty -> {}
        }
    }
}

private data object Empty : ExchangeState<Nothing>()
private data class Waiting<T>(val value: T) : ExchangeState<T>()
private data class Busy<T>(val value: T) : ExchangeState<T>()


internal class LockFreeExchanger<T> {
    private val slot = AtomicReference<ExchangeState<T>>(Empty)

    fun exchange(myItem: T, timeout: Duration): Result<T, TimeoutError> {
        val timeoutNano = timeout.toLong(DurationUnit.NANOSECONDS)
        val startTimeNano = System.nanoTime()

        while (true) {
            if (System.nanoTime() - startTimeNano >= timeoutNano) return Err(TimeoutError)

            when (val state = slot.get()) {
                Empty -> {
                    if (!slot.compareAndSet(state, Waiting(myItem))) continue

                    while (System.nanoTime() - startTimeNano < timeoutNano) {
                        slot.get().runBusy {
                            slot.set(Empty)
                            return Ok(it)
                        }
                    }

                    if (slot.compareAndSet(Waiting(myItem), Empty))
                        return Err(TimeoutError)

                    slot.get().runBusy {
                        slot.set(Empty)
                        return Ok(it)
                    }
                }

                is Waiting -> if (slot.compareAndSet(state, Busy(myItem)))
                    return Ok(state.value)

                is Busy -> {}
            }
        }
    }
}
