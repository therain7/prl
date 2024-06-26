package stack

import kotlin.time.Duration

class EliminationBackoffStack<T>(private val elimArrayCapacity: Int, elimMaxDuration: Duration) : Stack<T> {
    private val stack = CASStack<T>()
    private val eliminationArray =
        EliminationArray<T?>(elimArrayCapacity, elimMaxDuration)

    override fun top() = stack.top()?.value

    override fun push(value: T) {
        val node = CASStack.Node(value)
        while (true) {
            if (stack.tryPush(node).isOk()) return
            eliminationArray.visit(value, elimArrayCapacity).runOk {
                if (it == null) return
            }
        }
    }

    override fun pop(): T? {
        while (true) {
            stack.tryPop().runOk { return it?.value }
            eliminationArray.visit(null, elimArrayCapacity).runOk {
                if (it != null) return it
            }
        }
    }
}
