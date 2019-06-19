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
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import java.util.Objects
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A unit of work to be completed concurrently.
 */
typealias Task<T> = suspend () -> T

/**
 * A function which handles an [ActorEvent].
 */
typealias ActorEventHandler = (Any) -> Unit

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
     * Registers a function to handle actor events.
     *
     * The given [handler] is passed an object which identifies the task which triggered the event.
     *
     * @param [events] The events to register a handler for.
     * @param [handler] A function which is called when an event is triggered.
     */
    fun addEventHandler(vararg events: ActorEvent, handler: ActorEventHandler)

    /**
     * Sends the given [task] to the actor and returns its result.
     *
     * Each task has a [key] which can be used to identify it in an event handler.
     *
     * @param [task] The task to execute.
     * @param [key] An object used to identify the task which was sent.
     *
     * @see [Channel.send]
     */
    suspend fun <T> sendAsync(key: Any = Any(), task: Task<T>): Deferred<T>

    /**
     * Sends the given [task] to the actor and returns its result, blocking if the actor is full.
     *
     * Each task has a [key] which can be used to identify it in an event handler.
     *
     * @param [task] The task to execute.
     * @param [key] An object used to identify the task which was sent.
     *
     * @see [Channel.sendBlocking]
     */
    fun <T> sendBlockingAsync(key: Any = Any(), task: Task<T>): Deferred<T>

    /**
     * Suspends until all tasks which have been sent to this actor have been processed.
     */
    suspend fun flush()
}

/**
 * An event to send to a [ChannelTaskActor].
 */
private data class TaskActorEvent(val key: Any, val task: Task<Unit>)

private class ChannelTaskActor(private val channel: Channel<TaskActorEvent>) : TaskActor {

    /**
     * A map of event types to handlers for that event type.
     */
    private val eventHandlers: Map<ActorEvent, MutableList<ActorEventHandler>> = mapOf(
        ActorEvent.TASK_RECEIVED to mutableListOf(),
        ActorEvent.TASK_COMPLETED to mutableListOf(),
        ActorEvent.WAITING to mutableListOf(),
        ActorEvent.BUSY to mutableListOf()
    )

    override fun addEventHandler(vararg events: ActorEvent, handler: ActorEventHandler) {
        for (event in events) {
            eventHandlers[event]?.add(handler)
        }
    }

    /**
     * Triggers the given [event].
     *
     * @param [event] The event to trigger.
     * @param [key] An object which identifies the task which triggered the event.
     */
    fun triggerEvent(event: ActorEvent, key: Any) {
        eventHandlers[event]?.forEach { handler ->
            handler(key)
        }
    }

    override suspend fun <T> sendAsync(key: Any, task: Task<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        channel.send(
            TaskActorEvent(key) {
                try {
                    result.complete(task())
                } catch (e: Throwable) {
                    result.completeExceptionally(e)
                }
            }
        )
        return result
    }

    override fun <T> sendBlockingAsync(key: Any, task: Task<T>): Deferred<T> {
        val result = CompletableDeferred<T>()
        channel.sendBlocking(
            TaskActorEvent(key) {
                try {
                    result.complete(task())
                } catch (e: Throwable) {
                    result.completeExceptionally(e)
                }
            }
        )
        return result
    }

    override suspend fun flush() {
        val acknowledgement = sendAsync { true }
        acknowledgement.join()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelTaskActor) return false
        return channel == other.channel && eventHandlers == other.eventHandlers
    }

    override fun hashCode(): Int = Objects.hash(channel, eventHandlers)
}

/**
 * Creates a new actor for processing tasks.
 */
fun CoroutineScope.taskActor(
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.UNLIMITED,
    start: CoroutineStart = CoroutineStart.DEFAULT
): TaskActor {
    val channel = Channel<TaskActorEvent>(capacity)
    val actor = ChannelTaskActor(channel)

    launch(context = context, start = start) {
        while (true) {
            var (key, task) = channel.receive()

            actor.triggerEvent(ActorEvent.BUSY, key)

            while (true) {
                actor.triggerEvent(ActorEvent.TASK_RECEIVED, key)

                task()

                actor.triggerEvent(ActorEvent.TASK_COMPLETED, key)

                val event = channel.poll() ?: break
                key = event.key
                task = event.task
            }

            actor.triggerEvent(ActorEvent.WAITING, key)
        }
    }

    return actor
}

/**
 * Run the given [block] in the UI thread once this job completes.
 *
 * The [block] is passed the output of this job.
 */
infix fun <T> Deferred<T>.ui(block: suspend (T) -> Unit): Job {
    MainScope().launch { block(await()) }
    return this
}
