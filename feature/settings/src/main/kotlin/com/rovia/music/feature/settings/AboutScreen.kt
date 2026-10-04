@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.ui.R as CoreUiR

@Composable
fun AboutScreen(
    onBack: () -> Unit,
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
                            R.string.about_title,
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
                                R.string.about_back,
                            ),
                    )
                }
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 32.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.about_app_name,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
            )

            Text(
                text =
                    stringResource(
                        R.string.about_version,
                    ),
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )
        }
    }
}