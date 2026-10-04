@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.ui.R as CoreUiR

private val SettingsMenuOuterRadius =
    32.dp

private val SettingsMenuInnerRadius =
    0.dp

private val SettingsMenuShapeFirst =
    RoundedCornerShape(
        topStart =
            SettingsMenuOuterRadius,
        topEnd =
            SettingsMenuOuterRadius,
        bottomStart =
            SettingsMenuInnerRadius,
        bottomEnd =
            SettingsMenuInnerRadius,
    )

private val SettingsMenuShapeLast =
    RoundedCornerShape(
        topStart =
            SettingsMenuInnerRadius,
        topEnd =
            SettingsMenuInnerRadius,
        bottomStart =
            SettingsMenuOuterRadius,
        bottomEnd =
            SettingsMenuOuterRadius,
    )

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onFolderFilterClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.fillMaxSize(),
    ) {
        TopAppBar(
            modifier =
                Modifier.padding(
                    horizontal = 4.dp,
                ),
            title = {
                Text(
                    text =
                        stringResource(
                            R.string.settings_title,
                        ),
                )
            },
            navigationIcon = {
                FilledIconButton(
                    onClick = onBack,
                    shapes =
                        IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceContainerHigh,
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface,
                            ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                CoreUiR.drawable.ic_arrow_back,
                            ),
                        contentDescription =
                            stringResource(
                                R.string.settings_back,
                            ),
                    )
                }
            },
        )

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            verticalArrangement =
                Arrangement.spacedBy(
                    2.dp,
                ),
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    bottom = 36.dp,
                ),
        ) {
            item {
                SettingsFolderFilterItem(
                    onClick =
                        onFolderFilterClick,
                    shape =
                        SettingsMenuShapeFirst,
                )
            }

            item {
                SettingsAboutItem(
                    onClick =
                        onAboutClick,
                    shape =
                        SettingsMenuShapeLast,
                )
            }
        }
    }
}

@Composable
private fun SettingsFolderFilterItem(
    onClick: () -> Unit,
    shape: Shape,
) {
    SettingsMenuItem(
        onClick = onClick,
        shape = shape,
        icon =
            R.drawable.ic_folder_filter,
        title =
            R.string.folder_filter_title,
    )
}

@Composable
private fun SettingsAboutItem(
    onClick: () -> Unit,
    shape: Shape,
) {
    SettingsMenuItem(
        onClick = onClick,
        shape = shape,
        icon =
            R.drawable.ic_info,
        title =
            R.string.about_menu_title,
    )
}

@Composable
private fun SettingsMenuItem(
    onClick: () -> Unit,
    shape: Shape,
    icon: Int,
    title: Int,
) {
    val cardColors =
        CardDefaults.cardColors()

    FilledTonalButton(
        onClick = onClick,
        shape = shape,
        colors =
            ButtonDefaults
                .filledTonalButtonColors(
                    containerColor =
                        cardColors.containerColor,
                    contentColor =
                        cardColors.contentColor,
                ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                ),
        contentPadding =
            PaddingValues(
                horizontal = 16.dp,
                vertical = 14.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier.size(52.dp),
                horizontalArrangement =
                    Arrangement.Center,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onClick,
                    shapes =
                        IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults
                            .filledIconButtonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimary,
                            ),
                    modifier =
                        Modifier.size(52.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                icon,
                            ),
                        contentDescription = null,
                        modifier =
                            Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier =
                    Modifier.padding(
                        start = 16.dp,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            title,
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                )
            }
        }
    }
}