package stack

import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.DurationUnit

class ExpBackoffStack<T>(minBackoff: Duration, maxBackoff: Duration) : Stack<T> {
    private val minBackoffMillis = minBackoff.toLong(DurationUnit.MILLISECONDS)
    private val maxBackoffMillis = maxBackoff.toLong(DurationUnit.MILLISECONDS)
    private val stack = CASStack<T>()

    override fun top() = stack.top()?.value

    override fun push(value: T) {
        val node = CASStack.Node(value)
        var backoffMillis = minBackoffMillis
        while (true) {
            if (stack.tryPush(node).isOk()) return
            Thread.sleep(backoffMillis)
            backoffMillis = max(backoffMillis * 2, maxBackoffMillis)
        }
    }

    override fun pop(): T? {
        var backoffMillis = minBackoffMillis
        while (true) {
            stack.tryPop().runOk { return it?.value }
            Thread.sleep(backoffMillis)
            backoffMillis = max(backoffMillis * 2, maxBackoffMillis)
        }
    }
}
