package com.salat.gmediahud

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Environment
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.net.toUri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ecarx.xui.adaptapi.diminteraction.DimInteraction
import com.ecarx.xui.adaptapi.diminteraction.IMediaInteraction
import com.salat.gmediahud.components.NotificationSoundUtil
import com.salat.gmediahud.components.convertToFloat
import com.salat.gmediahud.components.extractUuidFromFilename
import com.salat.gmediahud.components.generateFileId
import com.salat.gmediahud.components.generateUuid
import com.salat.gmediahud.components.hasBitmapChanged
import com.salat.gmediahud.components.toast
import com.salat.gmediahud.datastore.DataStoreManager
import com.salat.gmediahud.datastore.Prefs
import com.salat.gmediahud.entity.CoverBuffer
import com.salat.gmediahud.entity.CustomSourceType
import com.salat.gmediahud.entity.ExternalTask
import com.salat.gmediahud.entity.LoadCoverStatus
import com.salat.gmediahud.entity.SpecialSource
import com.salat.gmediahud.entity.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BootAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TEMP_FOLDER = ".gmh_temp"
        private const val EVENTS_FOLDER = "events"
        private const val FILE_TYPE = ".jpg"
        private const val CACHE_FILE_BUFFER = 7 // +1 in real
        private const val JPEG_QUALITY = 90

        private const val DEFAULT_UPDATE_RATE = 1000L
        private const val DEFAULT_SOURCE_TYPE = CustomSourceType.ONLINE
        private const val SLEEP_DELAY = 5000L

        private const val HAVA_YM_PACKAGE = "yandex.auto.music"
    }

    private val stateChangeFlow = MutableSharedFlow<Pair<Int, String>>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var launchTime: Long = 0L

    private var updateRate = DEFAULT_UPDATE_RATE
    private var unknownArtistStub = false
    private var filterByAudioSource = false
    private var globalIsPlaying = false
    private var globalDataSyncEnabled = false
    private var globalForceSyncEnabled = false
    private var customSourceType: Int? = null

    private var mDimInteraction: DimInteraction? = null
    private var mMediaInteraction: IMediaInteraction? = null

    private var mediaSessionManager: MediaSessionManager? = null
    private var componentName: ComponentName? = null

    private var taskJob: Job? = null
    private var taskSleep: Job? = null

    private var lastVisiblePackage = ""

    private var taskManager: TaskManager? = null
    private var taskController: TaskController? = null
    private var jobQueueProcessor: Job? = null
    private var externalNonQueueTask: ExternalTask? = null
    private var externalQueueTask: ExternalTask? = null

    private var notificationSound: NotificationSoundUtil? = null

    // Active waiting pause flag
    private var needToUnpauseAfterEvent: Boolean = false
    private var unpausePackageEvent: String? = null

    private var lastTrackUpdate: TrackInfo? = null
    private val coverBufferList = mutableListOf<CoverBuffer>()

    private val playStateFlow: Flow<Boolean> = callbackFlow {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                super.onPlaybackConfigChanged(configs)
                val isPlaying = configs?.any { config ->
                    config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA
                } ?: false
                trySend(isPlaying)
            }
        }
        audioManager.registerAudioPlaybackCallback(callback, null)
        // Send the initial state
        trySend(audioManager.isMusicActive)
        awaitClose {
            audioManager.unregisterAudioPlaybackCallback(callback)
        }
    }.distinctUntilChanged()

    // -----------------------------------
    // Lifecycle
    // -----------------------------------

    override fun onCreate() {
        super.onCreate()
        launchTime = System.currentTimeMillis()
        Timber.d("[AS] Created")

        mDimInteraction = DimInteraction.create(applicationContext)
        mDimInteraction?.mediaInteraction?.let {
            mMediaInteraction = it
        }

        serviceScope.launch { GlobalState.canAccessibility.emit(true) }

        // Init TaskManager and TaskController.
        taskManager = TaskManager(serviceScope)
        taskManager?.let {
            taskController = TaskController(it, serviceScope)
        }
        // Run queue processor
        jobQueueProcessor = taskManager?.startQueueProcessor()
        // Notifications
        notificationSound = NotificationSoundUtil(this)

        // Freeze sync by pause
        serviceScope.launch {
            initPlayStopCollector()
            initPrefsCollector()
            initExternalJobCollector()
            initPackageCollector()
            initSourceFilterCollector()
        }
        postCreateWork()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        configureAccessibilityService()
        Timber.d("[AS] Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val windowId = event.windowId
            val packageName = event.packageName?.toString() ?: return
            stateChangeFlow.tryEmit(windowId to packageName)
        }

        GlobalState.lastAccessibilityEventTimestamp = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        Timber.d("[AS] Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            stopJobTask()
            mDimInteraction = null
            mMediaInteraction = null
            mediaSessionManager = null
            componentName = null
            lastTrackUpdate = null
            GlobalState.canAccessibility.emit(false)

            // Tasks
            jobQueueProcessor?.cancelAndJoin()
            jobQueueProcessor = null
            taskManager = null
            taskController = null

            // Notifications
            notificationSound?.release()
            notificationSound = null

            serviceScope.cancel()
        }
        Timber.d("[AS] Destroyed")
    }

    /**
     * Configures the Accessibility Service parameters.
     */
    private fun configureAccessibilityService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    private fun initMediaSession() {
        try {
            if (mediaSessionManager == null) {
                mediaSessionManager =
                    getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                componentName = ComponentName(this, MyNotificationListenerService::class.java)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    // -----------------------------------
    // Scan worker
    // -----------------------------------

    private fun fetchAndProcessTrackInfo(forceUpdate: Boolean = false) {
        initMediaSession()
        val controllers: List<MediaController> =
            mediaSessionManager?.getActiveSessions(componentName) ?: emptyList()

        // External task processing
        if (catchEventTask(forceUpdate)) return

        // If there are no active sessions, process empty data immediately
        if (controllers.isEmpty()) {
            Timber.d(
                "No active media sessions. Notification access " +
                        "may not be granted or nothing is playing."
            )
            sendTrackInfo(
                TrackInfo("", "", "", null, false),
                0,
                0,
                forceUpdate
            )
            return
        }

        // Iterate through all active sessions until the first one with non-null metadata and playbackState
        for (controller in controllers) {
            val metadata = controller.metadata
            val playbackState = controller.playbackState

            if (metadata != null && playbackState != null) {
                val sourcePackageName = controller.packageName

                // Extract track information
                val title =
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: "Unknown Title"
                val artist =
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                        ?: metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: "Unknown Artist"
                val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    ?: "Unknown Album"

                val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                val currentPositionMs = playbackState.position
                val isPlaying = playbackState.state == PlaybackState.STATE_PLAYING
                val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                val uniqueSign = if (mediaId != null && mediaId.trim().isNotEmpty()) {
                    mediaId
                } else (title + artist + album)
                val trackId = uniqueSign.generateFileId()

                val special = if (sourcePackageName == HAVA_YM_PACKAGE) {
                    SpecialSource.FROM_HTTP_ICON_URI
                } else SpecialSource.FROM_BITMAP

                // Generate a unique track identifier
                var cachedBuffer = getCoverBuffer(trackId)

                // Skip cover update if it's already loading
                if (cachedBuffer?.id == trackId && cachedBuffer.status == LoadCoverStatus.LOADING) {
                    // No action needed
                } else if (special == SpecialSource.FROM_BITMAP) { // Load cover like bitmap

                    // Retrieve cover URI if available
                    val albumArtUriStr =
                        metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI) ?: ""

                    // Attempt to get the cover Bitmap
                    val artBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

                    // Reset cover if it changed during playback
                    val needCoverReload = cachedBuffer?.status == LoadCoverStatus.READY &&
                            cachedBuffer.bitmap != null &&
                            hasBitmapChanged(cachedBuffer.bitmap, artBitmap)
                    if (needCoverReload) cachedBuffer = null

                    // Process cover if Bitmap is available
                    if (artBitmap != null && (cachedBuffer == null || cachedBuffer.id != trackId)) {

                        val newBuffer = CoverBuffer(
                            id = trackId,
                            mediaUri = albumArtUriStr,
                            fileUri = null,
                            bitmap = artBitmap,
                            status = LoadCoverStatus.LOADING
                        )
                        addCoverBuffer(newBuffer)

                        serviceScope.launch {
                            clearCachedArtworkFolder()
                            resolveAndExportArtworkBitmap(artBitmap, newBuffer)
                        }
                    }

                } else { // special == SpecialSource.FROM_HTTP_ICON_URI. Load cover like icon uri

                    val albumArtUriStr =
                        metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) ?: ""

                    if (cachedBuffer != null && cachedBuffer.mediaUri != albumArtUriStr) {

                        // TODO TEST
                        val oldArtFileName = cachedBuffer.mediaUri.generateUuid()
                        val oldFile = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                            "$TEMP_FOLDER/$oldArtFileName$FILE_TYPE"
                        )
                        if (oldFile.exists()) {
                            Timber.d("[AS] Delete old cover: ${oldFile.absolutePath}")
                            oldFile.delete()
                        }

                        cachedBuffer = null
                    }

                    val albumArtUri = albumArtUriStr.toUri()
                    val artFileName = albumArtUriStr.generateUuid()

                    // Check cache file already exist
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "$TEMP_FOLDER/$artFileName$FILE_TYPE"
                    )

                    // Get file if already exist or download and set
                    if (file.exists()) {
                        if (cachedBuffer == null) {
                            val newBuffer = CoverBuffer(
                                id = trackId,
                                mediaUri = albumArtUriStr,
                                fileUri = file.toUri(),
                                bitmap = null,
                                status = LoadCoverStatus.READY
                            )

                            addCoverBuffer(newBuffer)
                            cachedBuffer = newBuffer
                        }
                    } else if (cachedBuffer == null || cachedBuffer.status == LoadCoverStatus.ERROR) {
                        val newBuffer = CoverBuffer(
                            id = trackId,
                            mediaUri = albumArtUriStr,
                            fileUri = null,
                            bitmap = null,
                            status = LoadCoverStatus.LOADING
                        )
                        addCoverBuffer(newBuffer)

                        serviceScope.launch {
                            clearCachedArtworkFolder()
                            resolveAndExportArtworkHttpUri(
                                albumArtUri,
                                newBuffer,
                                fileName = artFileName
                            )
                        }
                    }
                }

                // Retrieve the prepared cover if available
                val preparedCover = cachedBuffer?.takeIf { it.id == trackId }?.fileUri

                // Send the collected data for further processing
                sendTrackInfo(
                    TrackInfo(
                        title = title, // && artist.isEmpty()
                        artist = if (unknownArtistStub && (artist.isEmpty() || artist == "Unknown Artist")) {
                            "\u00A0"
                        } else artist, // \u2009 \u2002 \u2003 &#160;
                        album = album,
                        cover = preparedCover,
                        isPlaying = isPlaying
                    ),
                    currentPositionMs,
                    durationMs,
                    forceUpdate
                )

                // Exit loop after processing the first valid session
                break
            }
        }
    }

    private fun catchEventTask(forceUpdate: Boolean = false): Boolean {
        // External task processing
        (externalNonQueueTask ?: externalQueueTask)?.let { task ->
            val taskId = task.time.toString().generateFileId()
            var preparedCover: Uri? = null

            if (task.art.startsWith("/storage/")) {
                try {
                    val buildUri = "file://${task.art}".toUri()

                    // Check file exist
                    val file = File(buildUri.path.toString())
                    if (file.exists()) {
                        preparedCover = buildUri
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            } else if (task.art.startsWith("https")) {

                val artFileName = "${launchTime}_${task.art}".generateUuid()

                // TODO move to out fun
                // Check cache file already exist
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "$TEMP_FOLDER/$EVENTS_FOLDER/$artFileName$FILE_TYPE"
                )

                // Get file if already exist or download and set
                if (file.exists()) {
                    preparedCover = file.toUri()
                    // Timber.d("[AS] FILE EXIST ${preparedCover}")
                } else {

                    // Generate a unique track identifier
                    val cachedBuffer: CoverBuffer? = getCoverBuffer(taskId)

                    // Skip cover update if it's already loading
                    if (cachedBuffer?.id == taskId && cachedBuffer.status == LoadCoverStatus.LOADING) {
                        // No action needed
                    } else if (cachedBuffer == null || cachedBuffer.id != taskId) { // Load cover by url

                        // Process cover if Bitmap is available

                        val newBuffer = CoverBuffer(
                            id = taskId,
                            mediaUri = task.art,
                            fileUri = null,
                            bitmap = null,
                            status = LoadCoverStatus.LOADING
                        )
                        addCoverBuffer(newBuffer)

                        serviceScope.launch {
                            clearCachedArtworkFolder(EVENTS_FOLDER)
                            resolveAndExportArtworkHttpUri(
                                task.art.toUri(),
                                newBuffer,
                                EVENTS_FOLDER,
                                artFileName
                            )
                        }
                    }

                    // Retrieve the prepared cover if available
                    preparedCover = cachedBuffer?.takeIf { it.id == taskId }?.fileUri
                    // Timber.d("[AS] FILE EXIST ${preparedCover}")
                }
            }

            sendTrackInfo(
                TrackInfo(
                    title = task.title,
                    artist = task.subtitle,
                    album = "",
                    cover = preparedCover,
                    isPlaying = false,
                    sourceType = task.sourceType,
                    progress = task.progress.toLong(),
                    maxProgress = task.maxProgress.toLong()
                ),
                0,
                0,
                forceUpdate
            )
            return true
        }
        return false
    }

    // -----------------------------------
    // Media interactor management
    // -----------------------------------

    private fun sendTrackInfo(
        track: TrackInfo,
        progressSeconds: Long,
        durationSeconds: Long,
        forceUpdate: Boolean
    ) {
        // Timber.d("[AS] processTrackInfo tick id:${track.cover?.extractUuidFromFilename()} $track")

        val lastTrack = lastTrackUpdate
        val trackChanged = lastTrack == null ||
                lastTrack.title != track.title ||
                lastTrack.artist != track.artist ||
                lastTrack.album != track.album ||
                lastTrack.cover != track.cover ||
                lastTrack.isPlaying != track.isPlaying ||
                lastTrack.sourceType != track.sourceType

        var progressBuffer = progressSeconds
        if (trackChanged || forceUpdate || globalForceSyncEnabled) {

            // No play data
            if (track.title.isEmpty() && track.artist.isEmpty()) {
                // Timber.d("[AS] TRACK NO CONTENT")

                clearUpdatePlaybackInfo()
                progressBuffer = 0
            } else {
                // Timber.d("[AS] TRACK UPDATE $track")

                // Change track update
                sendUpdatePlaybackInfo(track, durationSeconds)
            }
        }
        // else Timber.d("[AS] TRACK SKIP $track")

        lastTrackUpdate = track
        mMediaInteraction?.updateCurrentProgress(
            if (track.progress != -1L) {
                track.progress
            } else progressBuffer
        )
    }

    private fun sendUpdatePlaybackInfo(track: TrackInfo, durationSeconds: Long) {
        mMediaInteraction?.updatePlaybackInfo(object : IMediaInteraction.IPlaybackInfo {
            override fun getAlbum() = track.album

            override fun getArtist() = track.artist

            override fun getArtwork(): Uri? = track.cover

            override fun getCurrentLyricSentence() = ""

            override fun getDuration() = if (track.maxProgress != -1L) {
                track.maxProgress
            } else durationSeconds

            override fun getFavoriteState() = 1

            override fun getLoopMode() = 0

            override fun getLyric(): Uri? = null

            override fun getLyricContent(): String = ""

            override fun getMediaPath(): Uri? = null

            override fun getNextArtwork(): Uri? = track.cover

            override fun getPlaybackStatus(): Int = if (track.isPlaying) {
                IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PLAYING
            } else IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PAUSED

            override fun getPlayingItemPositionInQueue(): Int = 0

            override fun getPreviousArtwork(): Uri? = null

            override fun getRadioFrequency(): String = ""

            override fun getRadioMode(): Int =
                IMediaInteraction.IPlaybackInfo.RADIO_MODE_PLAYING

            override fun getRadioStationName(): String = ""

            override fun getSourceType(): Int = if (track.sourceType != -1) {
                track.sourceType
            } else customSourceType ?: DEFAULT_SOURCE_TYPE

            override fun getTitle(): String = track.title

            override fun getUUID(): String = track.cover?.extractUuidFromFilename() ?: ""
        })
    }

    private fun clearUpdatePlaybackInfo() {
        mMediaInteraction?.updatePlaybackInfo(object : IMediaInteraction.IPlaybackInfo {
            override fun getAlbum() = ""

            override fun getArtist() = ""

            override fun getArtwork(): Uri? = null

            override fun getCurrentLyricSentence() = ""

            override fun getDuration() = 0L

            override fun getFavoriteState() = 1

            override fun getLoopMode() = 0

            override fun getLyric(): Uri? = null

            override fun getLyricContent(): String = ""

            override fun getMediaPath(): Uri? = null

            override fun getNextArtwork(): Uri? = null

            override fun getPlaybackStatus(): Int =
                IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PAUSED

            override fun getPlayingItemPositionInQueue(): Int = 0

            override fun getPreviousArtwork(): Uri? = null

            override fun getRadioFrequency(): String = ""

            override fun getRadioMode(): Int =
                IMediaInteraction.IPlaybackInfo.RADIO_MODE_PLAYING

            override fun getRadioStationName(): String = ""

            override fun getSourceType(): Int = IMediaInteraction.SOURCE_TYPE_LOCAL

            override fun getTitle(): String = ""

            override fun getUUID(): String = ""
        })
    }

    // -----------------------------------
    // Files management
    // -----------------------------------

    private suspend fun resolveAndExportArtworkBitmap(
        bitmap: Bitmap,
        coverData: CoverBuffer
    ) {
        try {
            if (coverData.status != LoadCoverStatus.LOADING) {
                updateCoverBuffer(coverData.copy(status = LoadCoverStatus.LOADING))
            }

            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                TEMP_FOLDER
            ).apply { mkdirs() }

            val artworkFile = File(downloadsDir, "${UUID.randomUUID()}$FILE_TYPE")

            withContext(Dispatchers.IO) {
                FileOutputStream(artworkFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            }

            if (getCoverBuffer(coverData.id) != null) {
                updateCoverBuffer(
                    coverData.copy(
                        fileUri = Uri.fromFile(artworkFile),
                        status = LoadCoverStatus.READY
                    )
                )
                fetchAndProcessTrackInfo(true)
                return
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        updateCoverBuffer(coverData.copy(status = LoadCoverStatus.ERROR))
    }

    private suspend fun resolveAndExportArtworkHttpUri(
        mediaUri: Uri,
        coverData: CoverBuffer,
        subFolder: String = "",
        fileName: String? = null
    ) {
        try {
            if (coverData.status != LoadCoverStatus.LOADING) {
                updateCoverBuffer(coverData.copy(status = LoadCoverStatus.LOADING))
            }

            val request = ImageRequest.Builder(this)
                .data(mediaUri)
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        TEMP_FOLDER + if (subFolder.isNotEmpty()) "/$subFolder" else ""
                    ).apply { mkdirs() }

                    val artworkFile = File(
                        downloadsDir,
                        fileName?.let { "${it}$FILE_TYPE" } ?: "${UUID.randomUUID()}$FILE_TYPE"
                    )

                    withContext(Dispatchers.IO) {
                        FileOutputStream(artworkFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                        }
                    }

                    if (getCoverBuffer(coverData.id) != null) {
                        updateCoverBuffer(
                            coverData.copy(
                                fileUri = Uri.fromFile(artworkFile),
                                status = LoadCoverStatus.READY
                            )
                        )
                        fetchAndProcessTrackInfo(true)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        updateCoverBuffer(coverData.copy(status = LoadCoverStatus.ERROR))
    }

    private fun clearCachedArtworkFolder(subFolder: String = "") {
        val tempDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            TEMP_FOLDER + if (subFolder.isNotEmpty()) "/$subFolder" else ""
        )

        if (tempDir.exists() && tempDir.isDirectory) {
            // Get ONLY regular files from the main folder, without recursion
            val files = tempDir.listFiles { file -> file.isFile }
                ?.sortedBy { it.lastModified() }
                ?: return

            if (files.size > CACHE_FILE_BUFFER) {
                files.take(files.size - CACHE_FILE_BUFFER).forEach { file ->
                    try {
                        if (!file.delete()) {
                            Timber.d("Unable to delete file: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error deleting file ${file.name}")
                    }
                }
            }
        }
    }

    private fun checkAndDeleteOldCacheFolder(): Boolean {
        try {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "gmh_temp"
            )
            if (folder.exists() && folder.isDirectory) {
                return folder.deleteRecursively()
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return true
    }

    // -----------------------------------
    // Cover buffer management
    // -----------------------------------

    // Helper function to retrieve a cached buffer by id
    private fun getCoverBuffer(id: String): CoverBuffer? {
        return coverBufferList.find { it.id == id }
    }

    // Add a new buffer, respecting the cache size limit
    private fun addCoverBuffer(newBuffer: CoverBuffer) {
        // Remove any previous entry with the same id
        coverBufferList.removeAll { it.id == newBuffer.id }
        // If the list has reached the maximum size, remove the oldest entry
        if (coverBufferList.size >= CACHE_FILE_BUFFER + 1) {
            coverBufferList.removeAt(0)
        }
        coverBufferList.add(newBuffer)
    }

    // Update a buffer in the list by id
    private fun updateCoverBuffer(updated: CoverBuffer) {
        val index = coverBufferList.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            coverBufferList[index] = updated
        }
    }

    // -----------------------------------
    // Task management
    // -----------------------------------

    // Start a task if it isn't already running
    private fun startJobTask() {
        if (taskJob?.isActive == true) {
            // Task is already running, exit the method
            return
        }

        Timber.d("[AS] Start job task")

        taskJob = serviceScope.launch {
            while (isActive) {
                fetchAndProcessTrackInfo()
                delay(updateRate)
            }
        }
    }

    private fun stopJobTask(withLog: Boolean = true) {
        if (withLog) Timber.d("[AS] Stop job task")
        taskJob?.cancel()
        taskJob = null
    }

    private fun startSleepTask() {
        if (taskSleep?.isActive == true) {
            // Task is already running, exit the method
            return
        }

        taskSleep = serviceScope.launch {
            delay(SLEEP_DELAY)
            stopJobTask(false)
            Timber.d("[AS] Stopped by sleep task")
            stopSleepTask()
        }
    }

    private fun stopSleepTask() {
        taskSleep?.cancel()
        taskSleep = null
    }

    // -----------------------------------
    // Collectors
    // -----------------------------------

    // Freeze sync by pause
    private fun CoroutineScope.initPlayStopCollector() = launch {
        playStateFlow.collect { isPlaying ->
            if (globalDataSyncEnabled) {
                if (isPlaying) {
                    stopSleepTask()
                    startJobTask()
                } else {
                    startSleepTask()
                }
            }
            globalIsPlaying = isPlaying
        }
    }

    private fun CoroutineScope.initPackageCollector() = launch {
        stateChangeFlow.collect { (id, pkg) -> collectWindows(id, pkg) }
    }

    private fun collectWindows(windowId: Int, pkg: String) {
        try {
            val win = windows.firstOrNull { it.id == windowId } ?: return
            if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) return

            // Send GIB broadcast if visible package changed
            if (pkg != lastVisiblePackage) {
                lastVisiblePackage = pkg

                runCatching {
                    Intent().apply {
                        action = "com.salat.gbinder.SET_VISIBLE_PACKAGE"
                        setPackage("com.salat.gbinder")
                        putExtra("pkg", lastVisiblePackage)
                    }.also { sendBroadcast(it) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun CoroutineScope.initSourceFilterCollector() = launch {
        GlobalState.enableByAudioSource.collect { isEnabled ->
            if (filterByAudioSource && !isEnabled) {
                stopSleepTask()
                stopJobTask(false)
                Timber.d("[AS] Stopped by other source")
            }
        }
    }

    private fun CoroutineScope.initExternalJobCollector() = launch {
        launch {
            var skipInitSet = true
            taskManager?.currentQueueTask?.collect {
                if (!skipInitSet) {
                    externalQueueTask = it
                    if (externalNonQueueTask == null) { // TODO TEST THIS SHIT

                        // Sound
                        if (it?.withWarning == true) notificationSound?.playSound()

                        // Toast
                        if (it?.withToast == true && it.subtitle.isNotEmpty()) {
                            withContext(Dispatchers.Main) { toast(it.subtitle) }
                        }

                        // Pause and unpause
                        if (it?.withMediaPause == true && !needToUnpauseAfterEvent) {
                            val (result, targetPackage) = sendPlayOrPause(true)
                            if (result) {
                                needToUnpauseAfterEvent = true
                                unpausePackageEvent = targetPackage
                            }
                        } else if ((it == null || !it.withMediaPause) && needToUnpauseAfterEvent) {
                            val (result, _) = sendPlayOrPause(false, unpausePackageEvent)
                            if (result) {
                                needToUnpauseAfterEvent = false
                            }
                            unpausePackageEvent = null
                        }

                        fetchAndProcessTrackInfo(true)
                    }
                }
                skipInitSet = false
            }
        }
        launch {
            var skipInitSet = true
            taskManager?.currentNonQueueTask?.collect {
                if (!skipInitSet) {
                    externalNonQueueTask = it

                    // Sound
                    if (it?.withWarning == true) notificationSound?.playSound()

                    // Toast
                    if (it?.withToast == true && it.subtitle.isNotEmpty()) {
                        withContext(Dispatchers.Main) { toast(it.subtitle) }
                    }

                    // Pause and unpause
                    if (it?.withMediaPause == true && !needToUnpauseAfterEvent) {
                        val (result, targetPackage) = sendPlayOrPause(true)
                        if (result) {
                            needToUnpauseAfterEvent = true
                            unpausePackageEvent = targetPackage
                        }
                    } else if ((it == null || !it.withMediaPause) && needToUnpauseAfterEvent) {
                        val (result, _) = sendPlayOrPause(false, unpausePackageEvent)
                        if (result) {
                            needToUnpauseAfterEvent = false
                        }
                        unpausePackageEvent = null
                    }

                    fetchAndProcessTrackInfo(true)
                }
                skipInitSet = false
            }
        }
    }

    private fun sendPlayOrPause(
        pause: Boolean,
        targetPackageName: String? = null
    ): Pair<Boolean, String?> {
        var isToggle = false
        var affectedPackage: String? = null
        try {
            val controllers: List<MediaController> =
                mediaSessionManager?.getActiveSessions(componentName) ?: emptyList()

            if (pause) {
                // "Pause" mode: search for a controller that is playing (STATE_PLAYING)
                for (controller in controllers) {
                    val metadata = controller.metadata
                    val playbackState = controller.playbackState
                    if (metadata != null && playbackState != null && playbackState.state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls.pause()
                        isToggle = true
                        affectedPackage = controller.packageName
                        // Stop searching after the first matching session
                        break
                    }
                }
            } else {
                // "Play" mode: if targetPackageName is specified, search for that specific source
                if (targetPackageName != null) {
                    for (controller in controllers) {
                        if (controller.packageName == targetPackageName) {
                            val metadata = controller.metadata
                            val playbackState = controller.playbackState
                            if (metadata != null && playbackState != null && playbackState.state == PlaybackState.STATE_PAUSED) {
                                controller.transportControls.play()
                                isToggle = true
                                affectedPackage = controller.packageName
                            }
                            break  // Stop searching as we found the desired session
                        }
                    }
                }/* else {
                // Alternatively: if packageName is not specified, find the first paused source
                // and apply the play command to it.
                for (controller in controllers) {
                    val metadata = controller.metadata
                    val playbackState = controller.playbackState
                    if (metadata != null && playbackState != null && playbackState.state == PlaybackState.STATE_PAUSED) {
                        controller.transportControls.play()
                        isToggle = true
                        affectedPackage = controller.packageName
                        break
                    }
                }
            }*/
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return Pair(isToggle, affectedPackage)
    }

    private fun CoroutineScope.initPrefsCollector() = launch {
        val ds = DataStoreManager(this@BootAccessibilityService)
        launch {
            ds.getValueFlow(Prefs.MEDIA_DATA_SYNC_ENABLED).collect {
                if (globalIsPlaying) {
                    if (it == true) {
                        startJobTask()
                    } else {
                        stopJobTask()
                    }
                }
                globalDataSyncEnabled = it == true
            }
        }
        launch {
            ds.getValueFlow(Prefs.FORCE_SYNC).collect {
                globalForceSyncEnabled = it ?: false
            }
        }
        launch {
            ds.getValueFlow(Prefs.UPDATE_RATE).collect {
                updateRate = it?.toLong() ?: DEFAULT_UPDATE_RATE
            }
        }
        launch {
            ds.getValueFlow(Prefs.NOTIFICATION_VOLUME).collect {
                notificationSound?.setVolume(it?.convertToFloat() ?: .9f)
            }
        }
        launch {
            ds.getValueFlow(Prefs.UNKNOWN_ARTIST_STUB).collect {
                unknownArtistStub = it == true
            }
        }
        launch {
            ds.getValueFlow(Prefs.FILTER_BY_AUDIO_SOURCE).collect {
                filterByAudioSource = it == true
            }
        }
        launch {
            ds.getValueFlow(Prefs.MEDIA_SOURCE).collect {
                val needForceUpdate = customSourceType != null
                customSourceType = it ?: DEFAULT_SOURCE_TYPE

                if (needForceUpdate) {
                    // Timber.d("[AS] MODE FORCE REQUEST")
                    fetchAndProcessTrackInfo(true)
                }
            }
        }
    }

    private fun postCreateWork() {
        serviceScope.launch {
            checkAndDeleteOldCacheFolder()
        }
    }
}
