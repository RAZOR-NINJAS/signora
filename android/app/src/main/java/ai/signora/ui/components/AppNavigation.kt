package ai.signora.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import ai.signora.ui.theme.SignoraTheme

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TRANSLATE("Translate", Icons.Filled.Translate, Icons.Outlined.Translate),
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
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title
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
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.title
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
