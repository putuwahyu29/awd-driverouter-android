package com.awd.driverouter.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.awd.driverouter.BuildConfig
import com.awd.driverouter.R
import com.awd.driverouter.ui.screens.accounts.AccountsScreen
import com.awd.driverouter.ui.screens.accounts.AccountsViewModel
import com.awd.driverouter.ui.screens.files.FileBrowserScreen
import com.awd.driverouter.ui.screens.settings.SettingsScreen
import com.awd.driverouter.ui.screens.transfers.TransfersScreen
import com.awd.driverouter.ui.screens.about.AboutScreen
import com.awd.driverouter.ui.screens.backup.BackupScreen
import com.awd.driverouter.ui.screens.strategy.StrategyScreen
import com.awd.driverouter.util.NetworkObserver
import com.awd.driverouter.util.formatSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val labelRes: Int, val icon: @Composable () -> Unit) {
    object Home : Screen("files", R.string.nav_home, { Icon(Icons.Default.Home, null) })
    object Starred : Screen("starred", R.string.nav_starred, { Icon(Icons.Default.Star, null) })
    object Shared : Screen("shared", R.string.nav_shared, { Icon(Icons.Default.People, null) })
    object Trash : Screen("trash", R.string.nav_trash, { Icon(Icons.Default.Delete, null) })
    
    object Transfers : Screen("transfers", R.string.nav_transfers, { Icon(Icons.Default.SyncAlt, null) })
    object Strategy : Screen("strategy", R.string.upload_strategy, { Icon(Icons.Default.CompareArrows, null) })
    object Backup : Screen("backup", R.string.backup, { Icon(Icons.Default.CloudUpload, null) })
    object Accounts : Screen("accounts", R.string.nav_accounts, { Icon(Icons.Default.AccountCircle, null) })
    object Settings : Screen("settings", R.string.nav_settings, { Icon(Icons.Default.Settings, null) })
    object About : Screen("about", R.string.about, { Icon(Icons.Default.Info, null) })
}

@Composable
fun MainScreen(
    accountsViewModel: AccountsViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val bottomNavItems = listOf(Screen.Home, Screen.Starred, Screen.Shared, Screen.Trash)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val activeTransfers by mainViewModel.activeTransfers.collectAsState()
    val totalProgress by mainViewModel.totalProgress.collectAsState()
    
    val networkObserver = remember { NetworkObserver(context) }
    val networkStatus by networkObserver.observe.collectAsState(initial = NetworkObserver.Status.Available)
    val isOnline = networkStatus == NetworkObserver.Status.Available

    LaunchedEffect(Unit) {
        accountsViewModel.syncAllQuotas()
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    // Double Back to Exit Logic
    var backPressCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(backPressCount) {
        if (backPressCount > 0) {
            delay(2000)
            backPressCount = 0
        }
    }

    BackHandler(enabled = showBottomBar && drawerState.isClosed) {
        if (backPressCount == 0) {
            backPressCount++
            Toast.makeText(context, context.getString(R.string.exit_confirm), Toast.LENGTH_SHORT).show()
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }
    
    // Handle drawer closing on back press
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                navController = navController,
                accountsViewModel = accountsViewModel,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        },
        gesturesEnabled = showBottomBar
    ) {
        Scaffold(
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = activeTransfers.isNotEmpty(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            onClick = { navController.navigate(Screen.Transfers.route) },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            tonalElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.transfers_in_progress, activeTransfers.size),
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(totalProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { totalProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    if (showBottomBar) {
                        NavigationBar(
                            tonalElevation = 0.dp,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            val currentDestination = navBackStackEntry?.destination
                            bottomNavItems.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                NavigationBarItem(
                                    icon = screen.icon,
                                    label = { Text(stringResource(screen.labelRes)) },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .statusBarsPadding()
            ) {
                if (networkStatus != NetworkObserver.Status.Available) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.offline_message), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.weight(1f),
                    enterTransition = { fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { 30 }) },
                    exitTransition = { fadeOut(animationSpec = tween(300)) }
                ) {
                    composable(Screen.Home.route) { 
                        FileBrowserScreen(
                            mode = "files",
                            isOnline = isOnline,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        ) 
                    }
                    composable(Screen.Starred.route) { 
                        FileBrowserScreen(
                            mode = "starred",
                            isOnline = isOnline,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        ) 
                    }
                    composable(Screen.Shared.route) { 
                        FileBrowserScreen(
                            mode = "shared",
                            isOnline = isOnline,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        ) 
                    }
                    composable(Screen.Trash.route) {
                        FileBrowserScreen(
                            mode = "trash",
                            isOnline = isOnline,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                    composable(Screen.Transfers.route) { 
                        TransfersScreen(onBack = { navController.popBackStack() }) 
                    }
                    composable(Screen.Strategy.route) {
                        StrategyScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Backup.route) {
                        BackupScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Accounts.route) { 
                        AccountsScreen(onBack = { navController.popBackStack() }) 
                    }
                    composable(Screen.Settings.route) { 
                        SettingsScreen(onBack = { navController.popBackStack() }) 
                    }
                    composable(Screen.About.route) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
fun MainDrawerContent(
    navController: androidx.navigation.NavController,
    accountsViewModel: AccountsViewModel,
    onCloseDrawer: () -> Unit
) {
    val totalSpace by accountsViewModel.totalSpace.collectAsState()
    val usedSpace by accountsViewModel.usedSpace.collectAsState()
    val percentage by accountsViewModel.storagePercentage.collectAsState()
    
    val accounts by accountsViewModel.accounts.collectAsState()
    val hasAccounts = accounts.isNotEmpty()
    
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        windowInsets = WindowInsets.statusBars
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Storage Summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(24.dp)
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (hasAccounts && totalSpace > 0) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { percentage },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.storage_used, formatSize(usedSpace), formatSize(totalSpace)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (hasAccounts) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Storage: Unlimited / Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.no_accounts_connected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Drawer Navigation Items
            DrawerNavItem(
                label = stringResource(R.string.nav_transfers),
                icon = Icons.Default.SyncAlt,
                onClick = { onCloseDrawer(); navController.navigate(Screen.Transfers.route) }
            )

            DrawerNavItem(
                label = stringResource(R.string.upload_strategy),
                icon = Icons.Default.CompareArrows,
                onClick = { onCloseDrawer(); navController.navigate(Screen.Strategy.route) }
            )

            DrawerNavItem(
                label = stringResource(R.string.backup),
                icon = Icons.Default.CloudUpload,
                onClick = { onCloseDrawer(); navController.navigate(Screen.Backup.route) }
            )
            
            DrawerNavItem(
                label = stringResource(R.string.nav_accounts),
                icon = Icons.Default.AccountCircle,
                onClick = { onCloseDrawer(); navController.navigate(Screen.Accounts.route) }
            )
            
            DrawerNavItem(
                label = stringResource(R.string.nav_settings),
                icon = Icons.Default.Settings,
                onClick = { onCloseDrawer(); navController.navigate(Screen.Settings.route) }
            )
            
            DrawerNavItem(
                label = stringResource(R.string.about),
                icon = Icons.Default.Info,
                onClick = { onCloseDrawer(); navController.navigate(Screen.About.route) }
            )
            
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun DrawerNavItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        icon = { Icon(icon, null) },
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
