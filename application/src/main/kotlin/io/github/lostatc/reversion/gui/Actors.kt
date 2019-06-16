/*
 * Copyright © 2019 Garrett Powell
 *
 * This file is part of Reversion.
 *
 * Reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.gui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A unit of work to be completed concurrently.
 */
typealias Task<T> = suspend () -> T

/**
 * A channel that can return results asynchronously.
 */
class TaskChannel(channel: Channel<Task<Unit>>) : Channel<Task<Unit>> by channel {
    /**
     * Sends the given [task] to the channel and returns its result.
     *
     * @see [Channel.send]
     */
    suspend fun <T> sendAsync(task: Task<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        send {
            try {
                result.complete(task())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    /**
     * Sends the given [task] to the channel and returns its result, blocking if the channel is full.
     *
     * @see [Channel.sendBlocking]
     */
    fun <T> sendBlockingAsync(task: Task<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        sendBlocking {
            try {
                result.complete(task())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    /**
     * Suspends until all tasks which have been sent to this channel have been processed.
     */
    suspend fun flush() {
        val acknowledgement = sendAsync { true }
        acknowledgement.await()
    }
}

/**
 * Creates a new actor for processing tasks.
 *
 * @return A channel which tasks can be sent to.
 */
fun CoroutineScope.taskActor(
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.RENDEZVOUS,
    start: CoroutineStart = CoroutineStart.DEFAULT
): TaskChannel {
    val channel = Channel<Task<Unit>>(capacity)

    launch(context = context, start = start) {
        for (task in channel) {
            task()
        }
    }

    return TaskChannel(channel)
}
