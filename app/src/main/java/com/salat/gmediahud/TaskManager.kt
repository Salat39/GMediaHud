package com.salat.gmediahud

import com.salat.gmediahud.entity.ExternalTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * The TaskManager class is responsible for handling dynamic tasks.
 * It uses the provided CoroutineScope for structured concurrency.
 *
 * It is recommended to create an instance of this class in a layer with clear lifecycle management,
 * for example, in a repository tied to the lifecycle of a ViewModel or service.
 */
class TaskManager(
    private val scope: CoroutineScope
) {
    // Queue for sequential tasks (inQueue == true)
    private val taskQueue = mutableListOf<ExternalTask>()
    private val queueMutex = Mutex()

    // Signal flow to prevent the processor from polling an empty queue
    private val queueSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // For parallel tasks: list for storing (ExternalTask, Job)
    private val nonQueueMutex = Mutex()
    private val runningNonQueueTasks = mutableListOf<Pair<ExternalTask, Job>>()

    // MutableStateFlow to track the current queue task (for UI subscription)
    val currentQueueTask = MutableStateFlow<ExternalTask?>(null)

    // For parallel tasks, currentNonQueueTask will display the task with the most recent start time
    val currentNonQueueTask = MutableStateFlow<ExternalTask?>(null)

    // Variable to store the Job of the current queue task
    private var currentQueueJob: Job? = null

    /**
     * Starts the queue processor, which sequentially retrieves and executes tasks.
     * If the queue is empty, the processor suspends until a new task appears.
     */
    fun startQueueProcessor(): Job = scope.launch {
        while (isActive) {
            val nextTask = queueMutex.withLock {
                if (taskQueue.isNotEmpty()) taskQueue.removeAt(0) else null
            }
            if (nextTask != null) {
                currentQueueTask.value = nextTask
                // Launch the queue task as a child coroutine and save its Job for potential cancellation
                currentQueueJob = scope.launch {
                    executeQueueTask(nextTask)
                }
                // Wait for the task to complete (or be cancelled)
                currentQueueJob?.join()
                currentQueueJob = null
                currentQueueTask.value = null
            } else {
                // If the queue is empty, suspend until a signal is received
                queueSignal.first()
            }
        }
    }

    /**
     * Adds a task:
     * - If inQueue == true, adds to the queue and signals the processor.
     * - If inQueue == false, launches the task in parallel and saves its Job for cancellation.
     */
    suspend fun addTask(task: ExternalTask) {
        if (task.inQueue) {
            queueMutex.withLock {
                taskQueue.add(task)
            }
            // Signal the addition of a task so that waiting processor is resumed
            queueSignal.tryEmit(Unit)
        } else {
            // Launch the parallel task within the provided scope
            val job = scope.launch {
                try {
                    executeNonQueueTask(task)
                } finally {
                    // Upon task completion (or cancellation), remove this task and update the currentNonQueueTask:
                    nonQueueMutex.withLock {
                        runningNonQueueTasks.removeAll { it.first == task }
                        currentNonQueueTask.value = runningNonQueueTasks.lastOrNull()?.first
                    }
                }
            }
            // Add the new task and job to the running nonQueue list and update the display immediately
            nonQueueMutex.withLock {
                runningNonQueueTasks.add(task to job)
                currentNonQueueTask.value = task
            }
        }
    }

    /**
     * Executes a task taken from the queue.
     */
    private suspend fun executeQueueTask(task: ExternalTask) {
        Timber.d("Starting execution of queue task: ${task.title} (tag=${task.tag})")
        try {
            // Simulate task execution with a cancellable delay.
            delay(task.duration * 1000)
            Timber.d("Completed execution of queue task: ${task.title} (tag=${task.tag})")
        } catch (e: CancellationException) {
            Timber.d("Cancellation of queue task: ${task.title} (tag=${task.tag})")
            throw e
        }
    }

    /**
     * Executes a parallel task.
     */
    private suspend fun executeNonQueueTask(task: ExternalTask) {
        Timber.d("Starting execution of parallel task: ${task.title} (tag=${task.tag})")
        try {
            delay(task.duration * 1000)
            Timber.d("Completed execution of parallel task: ${task.title} (tag=${task.tag})")
        } catch (e: CancellationException) {
            Timber.d("Cancellation of parallel task: ${task.title} (tag=${task.tag})")
            throw e
        }
    }

    /**
     * Removes tasks with the specified tag from both the queue and interrupts running tasks
     * that match the criteria (tag matches, or if tag == "" then all tasks).
     */
    suspend fun removeTasks(tag: String) {
        // Remove pending tasks from the queue
        queueMutex.withLock {
            if (tag.isEmpty()) {
                Timber.d("Clearing all pending queue tasks")
                taskQueue.clear()
            } else {
                Timber.d("Removing queue tasks with tag: $tag")
                taskQueue.removeAll { it.tag == tag }
            }
        }
        // If the current queue task matches, cancel its Job.
        if (tag.isEmpty() || currentQueueTask.value?.tag == tag) {
            currentQueueJob?.cancel()
            Timber.d("Cancelling running queue task with tag: ${currentQueueTask.value?.tag}")
        }
        // Cancel all running parallel tasks that match the condition
        nonQueueMutex.withLock {
            val tasksToCancel = runningNonQueueTasks.filter { tag.isEmpty() || it.first.tag == tag }
            tasksToCancel.forEach { (_, job) ->
                job.cancel()
            }
            runningNonQueueTasks.removeAll { tag.isEmpty() || it.first.tag == tag }
            // Update currentNonQueueTask: if the cancelled task was displayed,
            // then display the most recently started task (or null if none remain)
            if (tag.isEmpty() || currentNonQueueTask.value?.tag == tag) {
                currentNonQueueTask.value = runningNonQueueTasks.lastOrNull()?.first
                Timber.d("Updating currentNonQueueTask to: ${currentNonQueueTask.value?.title}")
            }
        }
    }
}

/**
 * Controller for subscribing to events from GlobalState.
 * It can be created where TaskManager is created (e.g., in a repository or service).
 */
class TaskController(private val taskManager: TaskManager, scope: CoroutineScope) {
    init {
        // Subscribe to queue cleanup commands
        scope.launch {
            GlobalState.externalCleanTask.collect { tag ->
                taskManager.removeTasks(tag)
            }
        }
        // Subscribe to incoming tasks
        scope.launch {
            GlobalState.externalJobTask.collect { task ->
                taskManager.addTask(task)
            }
        }
    }
}
