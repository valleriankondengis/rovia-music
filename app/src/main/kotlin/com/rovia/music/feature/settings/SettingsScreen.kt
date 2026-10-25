@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.R

private val SettingsHorizontalPadding = 18.dp
private val SettingsVerticalSpacing = 1.dp

private val SettingsMenuIconSize = 52.dp
private val SettingsMenuIconContentSize = 24.dp

private val SettingsMenuContentPadding = PaddingValues(
    horizontal = 16.dp,
    vertical = 14.dp,
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onFolderFilterClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemNavigationBarInset =
        WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()

    val listBottomPadding = maxOf(
        36.dp,
        systemNavigationBarInset + 16.dp,
    )

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier = Modifier.padding(horizontal = 10.dp),
            title = {
                Text(
                    text = stringResource(R.string.settings_title),
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(
                            R.string.settings_back,
                        ),
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                SettingsVerticalSpacing,
            ),
            contentPadding = PaddingValues(
                start = SettingsHorizontalPadding,
                top = 8.dp,
                end = SettingsHorizontalPadding,
                bottom = listBottomPadding,
            ),
        ) {
            item(key = "settings_folder_filter") {
                SettingsMenuItem(
                    onClick = onFolderFilterClick,
                    segmentIndex = 0,
                    segmentCount = 2,
                    icon = R.drawable.ic_folder,
                    title = R.string.folder_filter_title,
                )
            }

            item(key = "settings_about") {
                SettingsMenuItem(
                    onClick = onAboutClick,
                    segmentIndex = 1,
                    segmentCount = 2,
                    icon = R.drawable.ic_settings,
                    title = R.string.about_menu_title,
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    onClick: () -> Unit,
    segmentIndex: Int,
    segmentCount: Int,
    icon: Int,
    title: Int,
) {
    SegmentedListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shapes = ListItemDefaults.segmentedShapes(
            index = segmentIndex,
            count = segmentCount,
        ),
        colors = ListItemDefaults.segmentedColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor =
                MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = SettingsMenuContentPadding,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(SettingsMenuIconSize)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(
                        SettingsMenuIconContentSize,
                    ),
                )
            }
        },
        content = {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}