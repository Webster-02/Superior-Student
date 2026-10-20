package com.superiorstudent.app.feature.studentconnect

import java.util.ArrayDeque

/**
 * Back stack for destinations hosted inside StudentConnectActivity.
 * The root destination is handled by the Activity, which exits U2U to ERP.
 */
internal class StudentConnectBackStack<T>(private val root: T) {
    private val previous = ArrayDeque<T>()

    fun navigate(current: T, destination: T) {
        if (current == destination) return
        if (destination == root) {
            clear()
            return
        }
        previous.addLast(current)
    }

    fun back(current: T): T {
        if (current == root) {
            clear()
            return root
        }
        return if (previous.isEmpty()) root else previous.removeLast()
    }

    /** Make Chat return to the chat list, whose parent is the U2U Home screen. */
    fun reset(parent: T, destination: T) {
        clear()
        if (parent != destination) previous.addLast(parent)
    }

    fun clear() {
        previous.clear()
    }

    internal fun sizeForTest(): Int = previous.size
}
