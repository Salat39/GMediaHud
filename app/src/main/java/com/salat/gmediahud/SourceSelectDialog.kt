package com.salat.gmediahud

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.salat.gmediahud.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectDialog(
    modifier: Modifier = Modifier,
    selected: Pair<Int, String>? = null,
    list: Map<Int, String> = emptyMap(),
    uiScaleState: Float,
    onDismiss: () -> Unit = {},
    onCancel: () -> Unit = { onDismiss() },
    onSelect: (Pair<Int, String>?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val dialogFillerSize =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) .2f else .1f
    val dialogHeightFiller =
        remember(configuration) { (configuration.screenHeightDp * dialogFillerSize).dp } // 20% of height

    BasicAlertDialog(
        modifier = modifier
            .padding(
                bottom = dialogHeightFiller,
                top = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    4.dp
                } else dialogHeightFiller
            )
            .padding(horizontal = 24.dp),
        onDismissRequest = {
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val density = LocalDensity.current
        val scaledDensity = remember(density, uiScaleState) {
            Density(
                density.density * (uiScaleState),
                density.fontScale * (uiScaleState)
            )
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Surface(
                modifier = Modifier
//                    .width(BASE_WIDTH.dp)
                    .fillMaxSize()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(8.dp),
                color = AppTheme.colors.surfaceBackground
            ) {
                Column(modifier = Modifier.padding(top = 22.dp)) {
                    Text(
                        text = stringResource(R.string.media_source),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = AppTheme.colors.contentPrimary,
                        style = AppTheme.typography.dialogTitle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var preSelected by remember { mutableStateOf(selected) }

                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(.1f))
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        item(key = -1) {
                            Spacer(
                                Modifier
                                    .height(.8.dp)
                            )
                        }
                        itemsIndexed(
                            items = list.entries.toList(),
                            key = { _, entry -> entry.key }
                        ) { _, (id, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { preSelected = Pair(id, name) }
                                    .padding(vertical = 2.dp)
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (preSelected?.first == id),
                                    onClick = { preSelected = Pair(id, name) },
                                    colors = RadioButtonColors(
                                        selectedColor = AppTheme.colors.contentPrimary.copy(.8f),
                                        unselectedColor = AppTheme.colors.contentPrimary.copy(.3f),
                                        disabledSelectedColor = AppTheme.colors.contentPrimary.copy(
                                            .3f
                                        ),
                                        disabledUnselectedColor = AppTheme.colors.contentPrimary.copy(
                                            .3f
                                        )
                                    )
                                )

                                Spacer(Modifier.width(6.dp))

                                Text(
                                    text = "$name ($id)",
                                    style = AppTheme.typography.dialogListTitle,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    color = AppTheme.colors.contentPrimary
                                )
                            }
                        }
                    }

                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(.1f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        Text(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            text = stringResource(android.R.string.cancel).uppercase(),
                            style = AppTheme.typography.dialogButton,
                            color = AppTheme.colors.contentAccent
                        )
                        Text(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onSelect(preSelected)
                                    onCancel()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            text = stringResource(android.R.string.ok).uppercase(),
                            style = AppTheme.typography.dialogButton,
                            color = AppTheme.colors.contentAccent
                        )
                    }

                }
            }
        }
    }
}
