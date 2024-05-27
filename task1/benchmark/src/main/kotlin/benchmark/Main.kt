package benchmark

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import stack.EliminationBackoffStack
import stack.ExpBackoffStack
import stack.LockFreeStack
import stack.Stack
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

/** Returns a random boolean that is true with probability p (0 = never, 1 = always) */
fun Random.nextBoolean(p: Double) = nextDouble() < p

fun fillStack(stack: Stack<Int>, count: Int) = repeat(count) { stack.push(it) }

fun bench(stack: Stack<Int>, threadsCount: Int, opsCount: Int, pushProbability: Double): Duration {
    val latch = CountDownLatch(1)
    val threads = List(threadsCount) {
        thread {
            latch.await()
            repeat(opsCount) {
                if (Random.nextBoolean(pushProbability)) stack.push(it)
                else stack.pop()
            }
        }
    }

    val totalTime = measureTime {
        latch.countDown()
        threads.forEach { it.join() }
    }
    return totalTime
}


fun main() {
    val threadsCount = listOf(4, 8, 16, 32)
    val pushProbability = 0.5
    val initElementsCount = 2_000_000
    val opsCount = 1_000_000

    val expMinBackoffNs = 10
    val expMaxBackoffNs = 50
    val elimMaxDurationNs = 50


    fun setupLockfreeStack() = LockFreeStack<Int>().also { fillStack(it, initElementsCount) }

    fun setupExpStack() = ExpBackoffStack<Int>(
        minBackoff = expMinBackoffNs.toDuration(DurationUnit.NANOSECONDS),
        maxBackoff = expMaxBackoffNs.toDuration(DurationUnit.NANOSECONDS)
    ).also { fillStack(it, initElementsCount) }

    fun setupElimStack(arrayCapacity: Int) = EliminationBackoffStack<Int>(
        elimArrayCapacity = arrayCapacity,
        elimMaxDuration = elimMaxDurationNs.toDuration(DurationUnit.NANOSECONDS),
    ).also { fillStack(it, initElementsCount) }


    val lockfreeTimes = mutableListOf<Int>()
    val expStackTimes = mutableListOf<Int>()
    val elimStackTimes = mutableListOf<Int>()

    threadsCount.forEach {
        lockfreeTimes.addLast(
            bench(setupLockfreeStack(), it, opsCount, pushProbability)
                .toInt(DurationUnit.MILLISECONDS)
        )
        expStackTimes.addLast(
            bench(setupExpStack(), it, opsCount, pushProbability)
                .toInt(DurationUnit.MILLISECONDS)
        )
        elimStackTimes.addLast(
            bench(setupElimStack(it / 2), it, opsCount, pushProbability)
                .toInt(DurationUnit.MILLISECONDS)
        )
    }

    val df = dataFrameOf(
        "threads" to threadsCount + threadsCount + threadsCount,
        "time" to lockfreeTimes + expStackTimes + elimStackTimes,
        "Backoff" to List(threadsCount.size) { "No backoff" }
                + List(threadsCount.size) { "Exp backoff" }
                + List(threadsCount.size) { "Elimination backoff" }
    )
    df.groupBy("Backoff").plot {
        line {
            x("threads") {
                axis.name = "Threads"
                scale = categorical()
            }
            y("time") { axis.name = "Execution time, ms" }
            color("Backoff")
        }
    }.save("res.png")
}
