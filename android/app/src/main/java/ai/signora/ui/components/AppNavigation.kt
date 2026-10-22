package ai.signora.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import ai.signora.ui.theme.SignoraTheme

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TRANSLATE("Translate", Icons.Filled.Translate, Icons.Outlined.Translate),
    STUDIO("Studio", Icons.Filled.ModelTraining, Icons.Outlined.ModelTraining),
    GAME("Dino", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    LEARN("Learn", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AppNavigationBar(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        AppDestination.values().forEach { destination ->
            val isSelected = currentDestination == destination
            val iconScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) 1.24f else 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.35f,
                    stiffness = 600f
                ),
                label = "NavIconScale"
            )
            val iconLift by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) -2f else 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.45f,
                    stiffness = 500f
                ),
                label = "NavIconLift"
            )
            val labelAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.72f,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "NavLabelAlpha"
            )
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationY = iconLift
                        }
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha }
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
fun AppNavigationRail(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        AppDestination.values().forEach { destination ->
            val isSelected = currentDestination == destination
            val iconScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSelected) 1.18f else 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.35f,
                    stiffness = 600f
                ),
                label = "RailIconScale"
            )
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    )
                },
                label = {
                    Text(text = destination.title)
                },
                alwaysShowLabel = true
            )
        }
    }
}

@Preview(name = "AppNavigationBar Preview")
@Composable
private fun AppNavigationBarPreview() {
    SignoraTheme(dynamicColor = false) {
        AppNavigationBar(
            currentDestination = AppDestination.TRANSLATE,
            onNavigate = {}
        )
    }
}
