package com.athletebridge.android.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.athletebridge.android.ui.theme.LogoGreen

@Composable
fun BottomNavBar(
    tabs: List<BottomNavItem>,
    selectedRoute: String,
    onTabSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        tabs.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute == item.screen.route,
                onClick = { onTabSelected(item.screen) },
                icon = {
                    if (item.badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text(item.badgeCount.toString()) }
                            }
                        ) {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LogoGreen,
                    selectedTextColor = LogoGreen,
                    indicatorColor = LogoGreen.copy(alpha = 0.12f)
                )
            )
        }
    }
}
