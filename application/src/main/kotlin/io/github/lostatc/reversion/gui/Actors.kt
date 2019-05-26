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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An action to be taken by an actor.
 */
sealed class ActorAction<T>

/**
 * An action which sends the given [element] to the actor.
 */
data class SendAction<T>(val element: T) : ActorAction<T>()

/**
 * An action which waits for the actor to finish processing all previously received actions.
 *
 * @param [acknowledgment] A future that completes and returns `true` when all previously received actions have been
 * processed.
 */
data class FlushAction<T>(val acknowledgment: CompletableDeferred<Boolean>) : ActorAction<T>()

/**
 * An actor which encapsulates a coroutine and a channel.
 *
 * This actor can have messages sent to it through a channel.
 */
data class FlushableActor<T>(private val job: Job, private val channel: Channel<ActorAction<T>>) : Job by job {
    /**
     * Closes the channel associated with this actor.
     *
     * @see [SendChannel.close]
     */
    fun close(cause: Throwable? = null): Boolean = channel.close(cause)

    /**
     * Sends [element] to this actor if it is possible to do so immediately.
     *
     * @see [SendChannel.offer]
     */
    fun offer(element: T): Boolean = channel.offer(SendAction(element))

    /**
     * Sends [element] to this actor.
     *
     * @see [SendChannel.send]
     */
    suspend fun send(element: T) {
        channel.send(SendAction(element))
    }

    /**
     * Sends [element] to this actor, blocking the caller if its channel is full.
     *
     * @see [SendChannel.sendBlocking]
     */
    fun sendBlocking(element: T) {
        channel.sendBlocking(SendAction(element))
    }

    /**
     * Suspends until all elements sent to this actor have been processed.
     */
    suspend fun flush() {
        val acknowledgement = CompletableDeferred<Boolean>()
        channel.send(FlushAction(acknowledgement))

        select<Unit> {
            acknowledgement.onAwait { }
            onJoin { }
        }
    }
}

/**
 * Creates a new [FlushableActor].
 *
 * @param [context] Context to use in addition to [CoroutineScope.coroutineContext].
 * @param [capacity] The capacity of the channel associated with this actor.
 * @param [start] How to start the coroutine.
 * @param [block] A function which processes each element sent to the actor.
 */
fun <T> CoroutineScope.flushableActor(
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.UNLIMITED,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.(T) -> Unit
): FlushableActor<T> {
    val channel = Channel<ActorAction<T>>(capacity = capacity)

    val job = launch(context = context, start = start) {
        for (action in channel) {
            when (action) {
                is FlushAction -> action.acknowledgment.complete(true)
                is SendAction -> block(action.element)
            }
        }
    }

    return FlushableActor(job, channel)
}
