package stack

import kotlin.random.Random
import kotlin.time.Duration

internal class EliminationArray<T>(capacity: Int, private val elimMaxDuration: Duration) {
    private val exchangers = Array(capacity) { LockFreeExchanger<T>() }

    fun visit(value: T, range: Int): Result<T, TimeoutError> {
        val slot = Random.nextInt(range)
        return (exchangers[slot].exchange(value, elimMaxDuration))
    }
}
