@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.rovia.music.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rovia.music.core.ui.R as CoreUiR

private const val LOGO_SIZE_DP = 128
private const val LOGO_ICON_SIZE_DP = 72

private val AboutMenuOuterRadius =
    32.dp

private val AboutMenuInnerRadius =
    0.dp

private val AboutMenuShapeFirst =
    RoundedCornerShape(
        topStart =
            AboutMenuOuterRadius,
        topEnd =
            AboutMenuOuterRadius,
        bottomStart =
            AboutMenuInnerRadius,
        bottomEnd =
            AboutMenuInnerRadius,
    )

private val AboutMenuShapeLast =
    RoundedCornerShape(
        topStart =
            AboutMenuInnerRadius,
        topEnd =
            AboutMenuInnerRadius,
        bottomStart =
            AboutMenuOuterRadius,
        bottomEnd =
            AboutMenuOuterRadius,
    )

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val applicationInfo =
        remember(context.packageName) {
            packageManager.getApplicationInfo(
                context.packageName,
                0,
            )
        }

    val packageInfo =
        remember(context.packageName) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        }

    val appName =
        remember(
            applicationInfo,
            packageManager,
        ) {
            applicationInfo
                .loadLabel(packageManager)
                .toString()
        }

    val versionName =
        packageInfo.versionName.orEmpty()

    val monochromeIcon =
        remember(context.packageName) {
            loadMonochromeAppIcon(context)
        }

    val versionText =
        stringResource(
            R.string.about_version_format,
            versionName,
        )

    val maintainerTitle =
        stringResource(
            R.string.about_maintainer,
        )

    val maintainerName =
        stringResource(
            R.string.about_maintainer_name,
        )

    val githubLabel =
        stringResource(
            R.string.about_github,
        )

    val githubRepository =
        stringResource(
            R.string.about_github_repository,
        )

    val githubUrl =
        stringResource(
            R.string.about_github_url,
        )

    val openGithub = {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(githubUrl),
                ),
            )
        } catch (_: ActivityNotFoundException) {
            // No browser activity is available.
        }
    }

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
                    .verticalScroll(
                        rememberScrollState(),
                    )
                    .padding(
                        horizontal = 12.dp,
                    )
                    .padding(
                        top = 20.dp,
                        bottom = 36.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                ),
        ) {
            /*
             * Rovia logo:
             * one circle only.
             */
            Box(
                modifier =
                    Modifier
                        .size(
                            LOGO_SIZE_DP.dp,
                        )
                        .clip(
                            CircleShape,
                        )
                        .background(
                            MaterialTheme
                                .colorScheme
                                .primaryContainer,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                monochromeIcon?.let { icon ->
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier =
                            Modifier.size(
                                LOGO_ICON_SIZE_DP.dp,
                            ),
                        colorFilter =
                            ColorFilter.tint(
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer,
                            ),
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp,
                    ),
            )

            Text(
                text = appName,
                style =
                    MaterialTheme
                        .typography
                        .headlineLarge,
            )

            Text(
                text = versionText,
                style =
                    MaterialTheme
                        .typography
                        .labelLarge,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        24.dp,
                    ),
            )

            AboutInformationGroup(
                maintainerTitle = maintainerTitle,
                maintainerName = maintainerName,
                githubLabel = githubLabel,
                githubRepository = githubRepository,
                onGithubClick = openGithub,
            )
        }
    }
}

@Composable
private fun AboutInformationGroup(
    maintainerTitle: String,
    maintainerName: String,
    githubLabel: String,
    githubRepository: String,
    onGithubClick: () -> Unit,
) {
    val cardColors =
        CardDefaults.cardColors()

    Column(
        modifier =
            Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                2.dp,
            ),
    ) {
        /*
         * Maintainer:
         * same visual geometry as the Settings menu,
         * but not clickable because it is information.
         */
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                    ),
            shape =
                AboutMenuShapeFirst,
            colors =
                cardColors,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp,
                        ),
            ) {
                Text(
                    text = maintainerTitle,
                    style =
                        MaterialTheme
                            .typography
                            .labelLarge,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            2.dp,
                        ),
                )

                Text(
                    text = maintainerName,
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

        /*
         * GitHub:
         * same FilledTonalButton visual language as Settings,
         * but text only.
         */
        FilledTonalButton(
            onClick = onGithubClick,
            shape =
                AboutMenuShapeLast,
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
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalAlignment =
                    Alignment.Start,
            ) {
                Text(
                    text = githubLabel,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface,
                )

                Text(
                    text = githubRepository,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        }
    }
}

private fun loadMonochromeAppIcon(
    context: Context,
): androidx.compose.ui.graphics.ImageBitmap? {
    val resourceId =
        context.resources.getIdentifier(
            "ic_launcher_monochrome",
            "drawable",
            context.packageName,
        )

    if (resourceId == 0) {
        return null
    }

    val drawable =
        context.resources
            .getDrawable(
                resourceId,
                context.theme,
            )
            .mutate()

    return drawableToImageBitmap(
        drawable = drawable,
    )
}

private fun drawableToImageBitmap(
    drawable: Drawable,
): androidx.compose.ui.graphics.ImageBitmap {
    val width =
        drawable.intrinsicWidth
            .takeIf { it > 0 }
            ?: 256

    val height =
        drawable.intrinsicHeight
            .takeIf { it > 0 }
            ?: 256

    val bitmap =
        Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )

    val canvas =
        android.graphics.Canvas(
            bitmap,
        )

    drawable.setBounds(
        0,
        0,
        width,
        height,
    )

    drawable.draw(canvas)

    return bitmap.asImageBitmap()
}