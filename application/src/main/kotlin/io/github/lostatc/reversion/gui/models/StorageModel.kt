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

package io.github.lostatc.reversion.gui.models

import io.github.lostatc.reversion.gui.ActorEventHandler
import io.github.lostatc.reversion.gui.TaskActor
import io.github.lostatc.reversion.gui.taskActor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel

/**
 * The model for coordinating access to the storage provider.
 */
object StorageModel : CoroutineScope by MainScope() {
    /**
     * An actor for sending tasks which storage and retrieve data from a repository.
     */
    val storageActor: TaskActor<TaskType> =
        taskActor(TaskType.DEFAULT, context = Dispatchers.IO, capacity = Channel.UNLIMITED)
}

/**
 * A type of task that can be filtered on in [ActorEventHandler] functions.
 */
enum class TaskType {
    /**
     * The default [TaskType].
     */
    DEFAULT,

    /**
     * A task which is called by an [ActorEventHandler] and should not trigger additional events.
     */
    HANDLER
}
