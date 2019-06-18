/*
 * Copyright © 2019 Wren Powell
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
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
 * An event that can occur in an actor.
 */
enum class ActorEvent {
    /**
     * An event that occurs whenever an actor receives a task.
     */
    TASK_RECEIVED,

    /**
     * An event that occurs whenever an actor completes a task.
     */
    TASK_COMPLETED,

    /**
     * An event that occurs when an actor finishes its last task and starts waiting for more.
     */
    WAITING,

    /**
     * An event that occurs when an actor receives its first task after a period of [WAITING].
     */
    BUSY
}

/**
 * An actor for executing tasks.
 */
interface TaskActor {
    /**
     * Registers a function to handle actor events of the given [type].
     */
    fun addEventHandler(type: ActorEvent, handler: () -> Unit)

    /**
     * Sends the given [task] to the actor and returns its result.
     *
     * @see [Channel.send]
     */
    suspend fun <T> sendAsync(task: Task<T>): Deferred<T>

    /**
     * Sends the given [task] to the actor and returns its result, blocking if the actor is full.
     *
     * @see [Channel.sendBlocking]
     */
    fun <T> sendBlockingAsync(task: Task<T>): Deferred<T>

    /**
     * Suspends until all tasks which have been sent to this actor have been processed.
     */
    suspend fun flush()
}

private data class ChannelTaskActor(
    private val channel: Channel<Task<Unit>>,
    private val eventHandlers: MutableList<ActorEventHandler> = mutableListOf()
) : TaskActor {

    override fun addEventHandler(type: ActorEvent, handler: () -> Unit) {
        eventHandlers.add(ActorEventHandler(type, handler))
    }

    /**
     * Triggers an event of the given [type].
     */
    fun triggerEvent(type: ActorEvent) {
        eventHandlers.filter { it.type == type }.forEach { it.handler() }
    }

    override suspend fun <T> sendAsync(task: Task<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        channel.send {
            try {
                result.complete(task())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    override fun <T> sendBlockingAsync(task: Task<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        channel.sendBlocking {
            try {
                result.complete(task())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    override suspend fun flush() {
        val acknowledgement = sendAsync { true }
        acknowledgement.join()
    }

    /**
     * A [handler] which is triggered on events of a certain [type].
     */
    private data class ActorEventHandler(val type: ActorEvent, val handler: () -> Unit)
}

/**
 * Creates a new actor for processing tasks.
 */
fun CoroutineScope.taskActor(
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.UNLIMITED,
    start: CoroutineStart = CoroutineStart.DEFAULT
): TaskActor {
    val channel = Channel<Task<Unit>>(capacity)
    val actor = ChannelTaskActor(channel)

    launch(context = context, start = start) {
        while (true) {
            var task: Task<Unit>? = channel.receive()
            actor.triggerEvent(ActorEvent.BUSY)

            while (task != null) {
                actor.triggerEvent(ActorEvent.TASK_RECEIVED)
                task()
                actor.triggerEvent(ActorEvent.TASK_COMPLETED)
                task = channel.poll()
            }

            actor.triggerEvent(ActorEvent.WAITING)
        }
    }

    return actor
}

/**
 * Run the given [block] in the UI thread once this job completes.
 */
infix fun <T> Deferred<T>.ui(block: suspend (T) -> Unit): Job {
    MainScope().launch { block(await()) }
    return this
}
