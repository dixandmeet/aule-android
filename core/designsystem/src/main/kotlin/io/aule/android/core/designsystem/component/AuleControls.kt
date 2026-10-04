package io.aule.android.core.designsystem.component

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.aule.android.core.designsystem.AuleTheme

/** Couleurs historiques des boutons Material encore présents dans les écrans Pro. */
@Composable
fun auleAccentButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = AuleTheme.tokens.accent.color,
    contentColor = AuleTheme.tokens.onAccent.color,
)

/** API historique Pro ; le chargement est celui du socle partagé. */
@Composable
fun AuleLoadingState(label: String, modifier: Modifier = Modifier) =
    io.aule.android.core.designsystem.states.AuleLoadingState(label = label, modifier = modifier)

/** API historique Pro ; les états vides sont communs aux deux applications. */
@Composable
fun AuleEmptyState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) = io.aule.android.core.designsystem.states.AuleEmptyState(
    title = title,
    detail = detail,
    modifier = modifier,
    icon = icon,
)
