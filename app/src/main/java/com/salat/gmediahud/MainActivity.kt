package com.salat.gmediahud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salat.gmediahud.components.isNotificationServiceEnabled
import com.salat.gmediahud.components.isPackageInstalled
import com.salat.gmediahud.components.openAccessibilitySettings
import com.salat.gmediahud.components.openAppSystemSettings
import com.salat.gmediahud.components.requestNotificationServicePermission
import com.salat.gmediahud.datastore.DataStoreManager
import com.salat.gmediahud.datastore.Prefs
import com.salat.gmediahud.entity.CustomSourceType
import com.salat.gmediahud.ui.BaseButton
import com.salat.gmediahud.ui.RenderSwitcher
import com.salat.gmediahud.ui.ValueSlider
import com.salat.gmediahud.ui.theme.AppTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

private const val DEFAULT_UPDATE_RATE = 1000
private const val DEFAULT_MEDIA_SOURCE = 6
private const val DEFAULT_NOTIFICATION_VOLUME = 90
private const val SUCHII_ES_PROVODNIK = "com.estrongs.android.pop"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.Transparent.toArgb()),
            SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        val ds = DataStoreManager(this)

        setContent {
            val uiScale = 1.5f
            val context = LocalContext.current
            val density = LocalDensity.current
            val scaledDensity = remember(density, uiScale) {
                Density(
                    density.density * uiScale,
                    density.fontScale * uiScale
                )
            }

            AppTheme(
                darkTheme = true
            ) {
                val scope = rememberCoroutineScope()
                val canAccessibility by GlobalState.canAccessibility.collectAsStateWithLifecycle()
                var isNotificationServiceEnabled by remember {
                    mutableStateOf(context.isNotificationServiceEnabled())
                }
                // var needFilePermission by remember { mutableStateOf(false) }

                var updateRate by remember { mutableIntStateOf(DEFAULT_UPDATE_RATE) }
                var mediaSource by remember { mutableIntStateOf(DEFAULT_MEDIA_SOURCE) }
                var notificationVolume by remember { mutableIntStateOf(DEFAULT_NOTIFICATION_VOLUME) }
                var isEnabled by remember { mutableStateOf(false) }
                var forceUpdate by remember { mutableStateOf(false) }
                var unknownArtistStub by remember { mutableStateOf(false) }
                var esExplorerStub by remember { mutableStateOf(false) }
                var filterByAudioSource by remember { mutableStateOf(false) }
                LaunchedEffect(true) {
                    updateRate =
                        ds.getValueFlow(Prefs.UPDATE_RATE).firstOrNull() ?: DEFAULT_UPDATE_RATE
                    mediaSource =
                        ds.getValueFlow(Prefs.MEDIA_SOURCE).firstOrNull() ?: DEFAULT_MEDIA_SOURCE
                    notificationVolume =
                        ds.getValueFlow(Prefs.NOTIFICATION_VOLUME).firstOrNull()
                            ?: DEFAULT_NOTIFICATION_VOLUME
                    isEnabled =
                        ds.getValueFlow(Prefs.MEDIA_DATA_SYNC_ENABLED).firstOrNull() ?: false
                    forceUpdate =
                        ds.getValueFlow(Prefs.FORCE_SYNC).firstOrNull() ?: false
                    unknownArtistStub =
                        ds.getValueFlow(Prefs.UNKNOWN_ARTIST_STUB).firstOrNull() ?: false
                    filterByAudioSource =
                        ds.getValueFlow(Prefs.FILTER_BY_AUDIO_SOURCE).firstOrNull() ?: false
                }
                LaunchedEffect(true) {
                    if (!esExplorerStub) {
                        esExplorerStub = context.isPackageInstalled(SUCHII_ES_PROVODNIK)
                    }
                }

                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                        val sourceTypeSelectDialog = remember { mutableStateOf(false) }
                        if (sourceTypeSelectDialog.value) {
                            SourceSelectDialog(
                                selected = Pair(mediaSource, mediaSource.getSourceName()),
                                list = sources.associateWith { it.getSourceName() },
                                uiScaleState = uiScale,
                                onDismiss = { sourceTypeSelectDialog.value = false },
                                onCancel = { sourceTypeSelectDialog.value = false },
                                onSelect = { pair ->
                                    pair?.let {
                                        mediaSource = it.first
                                        scope.launch {
                                            ds.saveValue(Prefs.MEDIA_SOURCE, it.first)
                                        }
                                    }
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppTheme.colors.surfaceBackground)
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (esExplorerStub) {

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp),
                                        text = stringResource(R.string.es_explorer_warning),
                                        textAlign = TextAlign.Center,
                                        style = AppTheme.typography.screenTitle.copy(
                                            lineHeight = 23.sp
                                        ),
                                        color = AppTheme.colors.contentPrimary
                                    )
                                    Spacer(Modifier.height(36.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        BaseButton(
                                            title = stringResource(R.string.uninstall),
                                            onClick = {
                                                context.openAppSystemSettings(SUCHII_ES_PROVODNIK)
                                            })
                                        Spacer(Modifier.width(26.dp))
                                        BaseButton(
                                            title = stringResource(R.string.ok),
                                            onClick = { esExplorerStub = false })
                                    }
                                }

                            } else if (!isNotificationServiceEnabled) {

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp),
                                        text = stringResource(R.string.grant_notification_access),
                                        textAlign = TextAlign.Center,
                                        style = AppTheme.typography.screenTitle.copy(
                                            lineHeight = 23.sp
                                        ),
                                        color = AppTheme.colors.contentPrimary
                                    )
                                    Spacer(Modifier.height(36.dp))
                                    BaseButton(
                                        title = stringResource(R.string.settings),
                                        onClick = { context.requestNotificationServicePermission() })
                                }
                            } else if (!canAccessibility) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp),
                                        text = stringResource(R.string.enable_accessibility),
                                        textAlign = TextAlign.Center,
                                        style = AppTheme.typography.screenTitle.copy(
                                            lineHeight = 23.sp
                                        ),
                                        color = AppTheme.colors.contentPrimary
                                    )
                                    Spacer(Modifier.height(36.dp))
                                    BaseButton(
                                        title = stringResource(R.string.accessibility_features),
                                        onClick = { context.openAccessibilitySettings() })
                                }
                            } else {
                                Spacer(Modifier.height(26.dp))

                                Text(
                                    modifier = Modifier,
                                    text = stringResource(
                                        if (isEnabled) {
                                            R.string.mediahud_active
                                        } else {
                                            R.string.mediahud_disabled
                                        }
                                    ),
                                    style = AppTheme.typography.dialogTitle,
                                    color = if (isEnabled) {
                                        AppTheme.colors.contentAccent
                                    } else {
                                        AppTheme.colors.sliderPassive
                                    }
                                )

                                Spacer(Modifier.height(48.dp))

                                // is enable
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.enable),
                                    subtitle = stringResource(R.string.media_data_sync),
                                    value = isEnabled,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        isEnabled = it
                                        scope.launch {
                                            ds.saveValue(Prefs.MEDIA_DATA_SYNC_ENABLED, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                // Sync rate
                                val sliderTitle = stringResource(R.string.refresh_rate)
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$sliderTitle: " +
                                            updateRate.toDecimalSecondString(),
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = updateRate,
                                    valueRange = 600..5000,
                                    onValueChange = { newValue ->
                                        updateRate = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.UPDATE_RATE, newValue)
                                        }
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_UPDATE_RATE,
                                    step = 100
                                )

                                Spacer(Modifier.height(12.dp))

                                // Force update
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.force_update),
                                    subtitle = stringResource(R.string.update_all_media_data),
                                    value = forceUpdate,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        forceUpdate = it
                                        scope.launch {
                                            ds.saveValue(Prefs.FORCE_SYNC, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(12.dp))

                                // Media source
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            sourceTypeSelectDialog.value = true
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            modifier = Modifier.padding(horizontal = 23.dp),
                                            text = stringResource(R.string.media_source),
                                            style = AppTheme.typography.screenTitle,
                                            color = AppTheme.colors.contentPrimary
                                        )

                                        Spacer(Modifier.height(5.dp))

                                        Text(
                                            text = stringResource(R.string.car_media_source_assumption),
                                            modifier = Modifier.padding(horizontal = 23.dp),
                                            color = AppTheme.colors.contentPrimary.copy(.4f),
                                            style = AppTheme.typography.dialogSubtitle
                                        )
                                    }

                                    Text(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AppTheme.colors.autoStart)
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        text = mediaSource.getSourceName(),
                                        color = AppTheme.colors.contentPrimary,
                                        style = AppTheme.typography.sourceType
                                    )

                                    Spacer(Modifier.width(20.dp))
                                }

                                Spacer(Modifier.height(12.dp))

                                // Unknown ArtistStub
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.subtitle_placeholder),
                                    subtitle = stringResource(R.string.remove_unknown_artist),
                                    value = unknownArtistStub,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        unknownArtistStub = it
                                        scope.launch {
                                            ds.saveValue(Prefs.UNKNOWN_ARTIST_STUB, it)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                // Sync rate
                                val notificationVolumeTitle =
                                    stringResource(R.string.notification_volume)
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 42.dp),
                                    textAlign = TextAlign.Left,
                                    text = "$notificationVolumeTitle: $notificationVolume%",
                                    color = AppTheme.colors.contentPrimary
                                )
                                ValueSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 36.dp),
                                    value = notificationVolume,
                                    valueRange = 0..100,
                                    onValueChange = { newValue ->
                                        notificationVolume = newValue
                                        scope.launch {
                                            ds.saveValue(Prefs.NOTIFICATION_VOLUME, newValue)
                                        }
                                    },
                                    enabled = true,
                                    defaultMark = DEFAULT_NOTIFICATION_VOLUME,
                                    step = 1
                                )

                                Spacer(Modifier.height(12.dp))

                                // Audio source filters
                                RenderSwitcher(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    title = stringResource(R.string.audio_source_filter),
                                    subtitle = stringResource(R.string.audio_source_filter_desc),
                                    value = filterByAudioSource,
                                    enable = true,
                                    groupDivider = false,
                                    onChange = {
                                        filterByAudioSource = it
                                        scope.launch {
                                            ds.saveValue(Prefs.FILTER_BY_AUDIO_SOURCE, it)
                                        }
                                    }
                                )


                                /*if (needFilePermission) {
                                    Spacer(Modifier.height(20.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 36.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                shape = RoundedCornerShape(16.dp),
                                                width = 1.dp,
                                                color = AppTheme.colors.contentWarning
                                            )
                                            .background(AppTheme.colors.contentWarning.copy(.2f))
                                            .clickable {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    val intent =
                                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                    val uri =
                                                        Uri.fromParts("package", packageName, null)
                                                    intent.setData(uri)
                                                    startActivity(intent)
                                                }
                                            }
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.grant_file_access),
                                            modifier = Modifier,
                                            color = AppTheme.colors.contentPrimary,
                                            style = AppTheme.typography.dialogSubtitle
                                        )
                                    }
                                }*/

                                Spacer(Modifier.height(64.dp))
                            }
                        }
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                isNotificationServiceEnabled =
                                    context.isNotificationServiceEnabled()

                                /*scope.launch(Dispatchers.IO) {
                                    needFilePermission =
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                                && !Environment.isExternalStorageManager()
                                }*/
                            }

                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
            }
        }
    }
}

private fun Int.toDecimalSecondString() =
    String.format(Locale.US, "%.1f сек", this / 1000.0)

private val sources: List<Int>
    get() = listOf(
        CustomSourceType.LOCAL,
        CustomSourceType.USB,
        CustomSourceType.BT,
        CustomSourceType.FM,
        CustomSourceType.AM,
        CustomSourceType.AUX,
        CustomSourceType.ONLINE,
        CustomSourceType.USB2,
        CustomSourceType.STATION,
        CustomSourceType.NET_NEWS,
        CustomSourceType.NET_VIDEO,
        CustomSourceType.DAB,
//        CustomSourceType.FAVORITE_FM,
//        CustomSourceType.FAVORITE_AM,
        CustomSourceType.FAVORITE_MUSIC,
//        CustomSourceType.AM_SCAN_LIST,
//        CustomSourceType.FM_SCAN_LIST,
    )

private fun Int.getSourceName() = when (this) {
    CustomSourceType.LOCAL -> "LOCAL"
    CustomSourceType.USB -> "USB"
    CustomSourceType.BT -> "BT"
    CustomSourceType.FM -> "FM"
    CustomSourceType.AM -> "AM"
    CustomSourceType.AUX -> "AUX"
    CustomSourceType.ONLINE -> "ONLINE"
    CustomSourceType.USB2 -> "USB2"
    CustomSourceType.STATION -> "STATION"
    CustomSourceType.NET_NEWS -> "NET_NEWS"
    CustomSourceType.NET_VIDEO -> "NET_VIDEO"
    CustomSourceType.DAB -> "DAB"
//    CustomSourceType.FAVORITE_FM -> "FAV_FM"
//    CustomSourceType.FAVORITE_AM -> "FAV_AM"
    CustomSourceType.FAVORITE_MUSIC -> "FAV_MUSIC"
//    CustomSourceType.AM_SCAN_LIST -> "AM_SCAN_LIST"
//    CustomSourceType.FM_SCAN_LIST -> "FM_SCAN_LIST"
    else -> "UNKNOWN"
}
