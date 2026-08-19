package com.nova.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.app.feature.appspace.AppProfileScreen
import com.nova.app.feature.appspace.AppSpaceScreen
import com.nova.app.feature.device.*
import com.nova.app.feature.detective.DetectiveScreen
import com.nova.app.feature.detective.appintel.AppIntelligenceScreen
import com.nova.app.feature.detective.appintel.PermissionMatrixScreen
import com.nova.app.feature.detective.file.FileInvestigatorScreen
import com.nova.app.feature.detective.image.ImageInvestigatorScreen
import com.nova.app.feature.detective.url.UrlInspectorScreen
import com.nova.app.feature.home.HomeScreen
import com.nova.app.feature.lab.*
import com.nova.app.feature.network.LanScanScreen
import com.nova.app.feature.network.NetworkDiagnosticsScreen
import com.nova.app.feature.network.NetworkScreen
import com.nova.app.feature.shield.ShieldAllowlistScreen
import com.nova.app.feature.shield.ShieldBlocklistScreen
import com.nova.app.feature.shield.ShieldScreen
import com.nova.app.feature.private_space.PrivateGated
import com.nova.app.feature.private_space.PrivateHomeScreen
import com.nova.app.feature.private_space.notes.NotesScreen
import com.nova.app.feature.private_space.vault.VaultScreen
import com.nova.app.feature.tools.*
import com.nova.app.feature.vision.BarcodeScannerScreen
import com.nova.app.feature.vision.OcrScreen
import com.nova.app.feature.vision.VisionScreen
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaSurface
import com.nova.app.ui.theme.NovaTextTertiary

private data class BottomTab(val destination: NovaDestination, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(NovaDestination.Home, "Home", Icons.Filled.Home),
    BottomTab(NovaDestination.Lab, "Lab", Icons.Filled.Science),
    BottomTab(NovaDestination.Device, "Device", Icons.Filled.PhoneAndroid),
    BottomTab(NovaDestination.Tools, "Tools", Icons.Filled.Build)
)

@Composable
fun NovaNavHost() {
    val navController = rememberNavController()
    val haptics = rememberNovaHaptics()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar(containerColor = NovaSurface) {
                bottomTabs.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.tick()
                            navController.navigate(tab.destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NovaAccent,
                            selectedTextColor = NovaAccent,
                            unselectedIconColor = NovaTextTertiary,
                            unselectedTextColor = NovaTextTertiary,
                            indicatorColor = NovaSurface
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NovaDestination.Home.route,
            modifier = Modifier.padding(padding),
            // A restrained depth transition: the incoming screen slides + fades in over the
            // outgoing one, which recedes with a faint scale-down rather than a hard cut.
            // Back navigation mirrors it — the previous screen settles back up to full scale
            // while the current one slides away — so the stack reads as a real place, not a
            // sequence of swapped panels.
            enterTransition = {
                fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 10 }
            },
            exitTransition = {
                fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                    scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.96f)
            },
            popEnterTransition = {
                fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.96f)
            },
            popExitTransition = {
                fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 10 }
            }
        ) {
            composable(NovaDestination.Home.route) {
                HomeScreen(onNavigate = { route -> navController.navigate(route) })
            }

            // Device
            composable(NovaDestination.Device.route) {
                DeviceScreen(onNavigateToTest = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.DeviceTouchTest.route) { TouchTestScreen() }
            composable(NovaDestination.DeviceDisplayTest.route) { DisplayTestScreen() }
            composable(NovaDestination.DeviceVibrationTest.route) { VibrationTestScreen() }
            composable(NovaDestination.DeviceFlashlightTest.route) { FlashlightTestScreen() }
            composable(NovaDestination.DeviceCameraTest.route) { CameraTestScreen() }

            // Lab
            composable(NovaDestination.Lab.route) {
                LabScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.LabMotion.route) { MotionGraphScreen() }
            composable(NovaDestination.LabRotation.route) { RotationScreen() }
            composable(NovaDestination.LabMagnetic.route) { MagneticFieldScreen() }
            composable(NovaDestination.LabLight.route) { LightMeterScreen() }
            composable(NovaDestination.LabSound.route) { SoundLevelScreen() }
            composable(NovaDestination.LabProximity.route) { ProximityScreen() }
            composable(NovaDestination.LabBarometer.route) { BarometerScreen() }
            composable(NovaDestination.LabGps.route) { GpsScreen() }
            composable(NovaDestination.LabSensorList.route) { SensorListScreen() }

            // Tools
            composable(NovaDestination.Tools.route) {
                ToolsScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.ToolCalculator.route) { CalculatorScreen() }
            composable(NovaDestination.ToolBase64.route) { Base64ToolScreen() }
            composable(NovaDestination.ToolUrlEncode.route) { UrlEncodeToolScreen() }
            composable(NovaDestination.ToolJson.route) { JsonFormatterScreen() }
            composable(NovaDestination.ToolUuid.route) { UuidGeneratorScreen() }
            composable(NovaDestination.ToolHash.route) { HashGeneratorScreen() }
            composable(NovaDestination.ToolTimestamp.route) { TimestampConverterScreen() }
            composable(NovaDestination.ToolUnitConverter.route) { UnitConverterScreen() }

            // Network
            composable(NovaDestination.Network.route) {
                NetworkScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.NetworkDiagnostics.route) { NetworkDiagnosticsScreen() }
            composable(NovaDestination.NetworkLanScan.route) { LanScanScreen() }

            // Vision
            composable(NovaDestination.Vision.route) {
                VisionScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.VisionBarcode.route) { BarcodeScannerScreen() }
            composable(NovaDestination.VisionOcr.route) { OcrScreen() }

            // Privacy — upgraded in this phase into App Intelligence (same underlying repository,
            // no duplicated infrastructure). The original PrivacyScreen/PrivacyViewModel remain
            // in the codebase and still work; this route now composes the richer detective view.
            composable(NovaDestination.Privacy.route) { AppIntelligenceScreen() }

            // App Space
            composable(NovaDestination.AppSpace.route) {
                AppSpaceScreen(onOpenProfile = { pkg -> navController.navigate(NovaDestination.AppProfile.routeFor(pkg)) })
            }
            composable(
                route = NovaDestination.AppProfile.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName").orEmpty()
                AppProfileScreen(packageName = packageName)
            }

            // Detective
            composable(NovaDestination.Detective.route) {
                DetectiveScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.DetectiveUrl.route) { UrlInspectorScreen() }
            composable(NovaDestination.DetectiveFile.route) { FileInvestigatorScreen() }
            composable(NovaDestination.DetectiveImage.route) { ImageInvestigatorScreen() }
            composable(NovaDestination.DetectivePermissionMatrix.route) { PermissionMatrixScreen() }

            // Shield
            composable(NovaDestination.Shield.route) {
                ShieldScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(NovaDestination.ShieldBlocklist.route) { ShieldBlocklistScreen() }
            composable(NovaDestination.ShieldAllowlist.route) { ShieldAllowlistScreen() }

            // Private — every destination is wrapped in PrivateGated, which shows the auth gate
            // whenever the session is locked and only composes real content once unlocked.
            composable(NovaDestination.PrivateHome.route) {
                PrivateGated { PrivateHomeScreen(onNavigate = { route -> navController.navigate(route) }) }
            }
            composable(NovaDestination.PrivateVault.route) {
                PrivateGated { VaultScreen() }
            }
            composable(NovaDestination.PrivateNotes.route) {
                PrivateGated { NotesScreen() }
            }
        }
    }
}
