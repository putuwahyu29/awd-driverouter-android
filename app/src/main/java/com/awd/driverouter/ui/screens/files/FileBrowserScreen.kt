package com.awd.driverouter.ui.screens.files

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.awd.driverouter.R
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.ui.components.ShareDialog
import com.awd.driverouter.ui.components.BrandIcon
import com.awd.driverouter.ui.screens.accounts.AccountsViewModel
import com.awd.driverouter.util.FileIconHelper
import com.awd.driverouter.util.formatSize
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    mode: String = "files",
    isOnline: Boolean = true,
    onMenuClick: () -> Unit,
    viewModel: FilesViewModel = hiltViewModel(),
    accountsViewModel: AccountsViewModel = hiltViewModel()
) {
    LaunchedEffect(mode) {
        viewModel.setMode(mode)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val folderPath by viewModel.folderPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val searchScope by viewModel.searchScope.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val isSelectionMode = selectedFileIds.isNotEmpty()
    
    val accounts by accountsViewModel.accounts.collectAsState()
    val hasAccounts = accounts.isNotEmpty()
    
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    var isGridView by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showAddOptions by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    
    var selectedFileForAction by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForPreview by remember { mutableStateOf<CloudFile?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    val filesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadFiles(uris)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.uploadFolder(uri)
        }
    }

    BackHandler(enabled = (folderPath.size > 1) || selectedFileForPreview != null || isSearchMode) {
        if (selectedFileForPreview != null) {
            selectedFileForPreview = null
        } else if (isSearchMode) {
            isSearchMode = false
            viewModel.onSearchQueryChange("")
        } else {
            viewModel.navigateBack()
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                val selectedFile = (uiState as? FilesUiState.Success)?.files?.find { it.id == selectedFileIds.first() }
                ContextualTopAppBar(
                    selectedCount = selectedFileIds.size,
                    showShare = selectedFileIds.size == 1 && selectedFile?.isOwner == true,
                    onClearSelection = { viewModel.clearSelection() },
                    onDelete = { showDeleteConfirm = true },
                    onDownload = { 
                        // Download all selected
                        Toast.makeText(context, context.getString(R.string.downloading_multiple, selectedFileIds.size), Toast.LENGTH_SHORT).show()
                        viewModel.clearSelection()
                    },
                    onShare = {
                        val selectedFile = (viewModel.uiState.value as? FilesUiState.Success)?.files?.find { it.id == selectedFileIds.first() }
                        if (selectedFile != null) {
                            selectedFileForAction = selectedFile
                            if (selectedFile.supportsNativeSharing) {
                                showShareDialog = true
                            } else {
                                selectedFile.webViewLink?.let { url ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, url)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                }
                            }
                        }
                        viewModel.clearSelection()
                    }
                )
            } else {
                val isRoot = folderPath.size <= 1
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (isRoot) {
                        UnifiedSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onMenuClick = onMenuClick,
                            isGridView = isGridView,
                            onToggleView = { isGridView = !isGridView },
                            onRefresh = viewModel::refresh,
                            isOnline = isOnline
                        )
                        
                        PageHeader(
                            mode = mode, 
                            folderName = folderPath.last().name,
                            showAccountFilter = true,
                            accounts = accounts,
                            selectedAccountId = selectedAccountId,
                            onAccountSelected = viewModel::selectAccount
                        )
                    } else {
                        if (isSearchMode) {
                            UnifiedSearchBar(
                                query = searchQuery,
                                onQueryChange = viewModel::onSearchQueryChange,
                                onMenuClick = { 
                                    isSearchMode = false
                                    viewModel.onSearchQueryChange("")
                                },
                                isGridView = isGridView,
                                onToggleView = { isGridView = !isGridView },
                                onRefresh = viewModel::refresh,
                                isSearchMode = true,
                                isOnline = isOnline
                            )
                        } else {
                            CompactTopAppBar(
                                title = folderPath.last().name,
                                isGridView = isGridView,
                                onToggleView = { isGridView = !isGridView },
                                onSearchClick = { isSearchMode = true },
                                onRefresh = viewModel::refresh,
                                onBack = { viewModel.navigateBack() },
                                isOnline = isOnline
                            )
                        }
                        
                        BreadcrumbsRow(
                            path = folderPath, 
                            onNavigate = viewModel::navigateToBreadcrumb
                        )
                    }
                    
                    SmartFilterRow(
                        selectedCategory = categoryFilter,
                        onCategorySelected = viewModel::setCategoryFilter,
                        selectedSortOrder = sortOrder,
                        onSortOrderSelected = viewModel::setSortOrder
                    )

                    if (searchQuery.isNotEmpty() && folderPath.size > 1) {
                        SearchScopeRow(
                            selectedScope = searchScope,
                            currentFolderName = folderPath.lastOrNull()?.name ?: "",
                            onScopeSelected = viewModel::setSearchScope
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    if (!isOnline) {
                        Toast.makeText(context, context.getString(R.string.offline_message), Toast.LENGTH_SHORT).show()
                        return@FloatingActionButton
                    }
                    if (hasAccounts) {
                        showAddOptions = true 
                    } else {
                        Toast.makeText(context, context.getString(R.string.connect_account_first), Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = if (hasAccounts && isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hasAccounts && isOnline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                elevation = FloatingActionButtonDefaults.elevation(if (isOnline) 4.dp else 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add), modifier = Modifier.size(32.dp))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isRefreshing && uiState is FilesUiState.Success) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is FilesUiState.Loading -> LoadingShimmerList()
                    is FilesUiState.Success -> {
                        if (state.files.isEmpty()) {
                            EmptyState(isSearch = searchQuery.isNotEmpty())
                        } else {
                            if (isGridView) {
                                FileGrid(
                                    files = state.files,
                                    selectedIds = selectedFileIds,
                                    onClick = { 
                                        if (isSelectionMode) viewModel.toggleSelection(it.id)
                                        else if (it.isFolder) viewModel.navigateToFolder(it) 
                                        else selectedFileForPreview = it
                                    },
                                    onLongClick = { viewModel.toggleSelection(it.id) },
                                    onMoreClick = {
                                        selectedFileForAction = it
                                        showActionSheet = true
                                    }
                                )
                            } else {
                                FileList(
                                    files = state.files,
                                    selectedIds = selectedFileIds,
                                    onClick = { 
                                        if (isSelectionMode) viewModel.toggleSelection(it.id)
                                        else if (it.isFolder) viewModel.navigateToFolder(it) 
                                        else selectedFileForPreview = it
                                    },
                                    onLongClick = { viewModel.toggleSelection(it.id) },
                                    onMoreClick = {
                                        selectedFileForAction = it
                                        showActionSheet = true
                                    }
                                )
                            }
                        }
                    }
                    is FilesUiState.Error -> ErrorState(state.message) { viewModel.refresh() }
                }
            }
        }
    }

    // Modals
    if (showAddOptions) {
        AddActionBottomSheet(
            onDismiss = { showAddOptions = false },
            onCreateFolder = { showCreateFolderDialog = true },
            onUploadFiles = { filesPickerLauncher.launch(arrayOf("*/*")) },
            onUploadFolder = { folderPickerLauncher.launch(null) }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(folderName, { folderName = it }, { showCreateFolderDialog = false }) {
            viewModel.createFolder(folderName)
            showCreateFolderDialog = false
            folderName = ""
        }
    }

    if (showActionSheet && selectedFileForAction != null) {
        FileActionBottomSheet(
            file = selectedFileForAction!!,
            onDismiss = { showActionSheet = false },
            onAction = { actionType ->
                showActionSheet = false
                when (actionType) {
                    "download" -> viewModel.downloadFile(selectedFileForAction!!)
                    "delete" -> showDeleteConfirm = true
                    "rename" -> {
                        newFileName = selectedFileForAction!!.name
                        showRenameDialog = true
                    }
                    "details" -> showDetailsDialog = true
                    "share" -> {
                        if (selectedFileForAction!!.supportsNativeSharing) {
                            showShareDialog = true
                        } else {
                            selectedFileForAction!!.webViewLink?.let { url ->
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, url)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }
                        }
                    }
                    "open_native" -> {
                        val localFile = viewModel.getLocalFile(selectedFileForAction!!)
                        if (localFile != null) {
                            try {
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    localFile
                                )
                                val extension = localFile.extension
                                val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                                
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(contentUri, mimeType ?: selectedFileForAction!!.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            selectedFileForAction!!.webViewLink?.let { url ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                                }
                            } ?: run {
                                Toast.makeText(context, context.getString(R.string.downloading_to_open), Toast.LENGTH_SHORT).show()
                                viewModel.downloadFile(selectedFileForAction!!)
                            }
                        }
                    }
                    "web" -> {
                        selectedFileForAction!!.webViewLink?.let { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val provider = selectedFileForAction?.provider?.lowercase() ?: ""
        val isPermanent = provider == "dropbox" || provider == "webdav" || provider == "sftp"
        
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Column {
                    Text(stringResource(R.string.confirm_delete))
                    if (isPermanent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.delete_permanent_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteFile(selectedFileForAction!!)
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRenameDialog && selectedFileForAction != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text(stringResource(R.string.folder_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameFile(selectedFileForAction!!, newFileName)
                    showRenameDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDetailsDialog && selectedFileForAction != null) {
        FileDetailsDialog(selectedFileForAction!!) { showDetailsDialog = false }
    }

    if (showShareDialog && selectedFileForAction != null) {
        val liveFile = (uiState as? FilesUiState.Success)?.files?.find { it.id == selectedFileForAction?.id } ?: selectedFileForAction!!
        val permissions by viewModel.permissions.collectAsState()
        val isLoadingPermissions by viewModel.isPermissionsLoading.collectAsState()
        
        ShareDialog(
            file = liveFile,
            permissions = permissions,
            isLoadingPermissions = isLoadingPermissions,
            onDismiss = { showShareDialog = false },
            onShare = { email, role ->
                viewModel.shareFile(liveFile, email, role)
            },
            onSetGeneralAccess = { isPublic ->
                viewModel.setGeneralAccess(liveFile, isPublic)
            },
            onRefresh = {
                viewModel.loadPermissions(liveFile)
            }
        )
    }

    if (selectedFileForPreview != null) {
        Dialog(
            onDismissRequest = { selectedFileForPreview = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullFilePreview(
                file = selectedFileForPreview!!,
                viewModel = viewModel,
                onBack = { selectedFileForPreview = null },
                onDownload = { 
                    viewModel.downloadFile(selectedFileForPreview!!)
                    selectedFileForPreview = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScopeRow(
    selectedScope: SearchScope,
    currentFolderName: String,
    onScopeSelected: (SearchScope) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedScope == SearchScope.CURRENT_FOLDER,
            onClick = { onScopeSelected(SearchScope.CURRENT_FOLDER) },
            label = { Text(stringResource(R.string.search_this_folder)) },
            leadingIcon = if (selectedScope == SearchScope.CURRENT_FOLDER) {
                { Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp)) }
            } else null
        )
        FilterChip(
            selected = selectedScope == SearchScope.ALL,
            onClick = { onScopeSelected(SearchScope.ALL) },
            label = { Text(stringResource(R.string.search_all)) },
            leadingIcon = if (selectedScope == SearchScope.ALL) {
                { Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp)) }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFilterRow(
    selectedCategory: FileCategory,
    onCategorySelected: (FileCategory) -> Unit,
    selectedSortOrder: SortOrder,
    onSortOrderSelected: (SortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(FileCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { 
                        Text(
                            when(category) {
                                FileCategory.ALL -> stringResource(R.string.filter_all)
                                FileCategory.IMAGE -> stringResource(R.string.filter_images)
                                FileCategory.VIDEO -> stringResource(R.string.filter_videos)
                                FileCategory.AUDIO -> stringResource(R.string.filter_audio)
                                FileCategory.DOCUMENT -> stringResource(R.string.filter_docs)
                            }
                        ) 
                    },
                    leadingIcon = if (selectedCategory == category) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = CircleShape
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.sort),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                when(order) {
                                    SortOrder.NAME -> stringResource(R.string.sort_name)
                                    SortOrder.DATE -> stringResource(R.string.sort_date)
                                    SortOrder.SIZE -> stringResource(R.string.sort_size)
                                }
                            ) 
                        },
                        onClick = { 
                            onSortOrderSelected(order)
                            showSortMenu = false 
                        },
                        leadingIcon = {
                            if (selectedSortOrder == order) {
                                Icon(Icons.Default.Check, null)
                            }
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun PageHeader(
    mode: String, 
    folderName: String, 
    onBack: (() -> Unit)? = null,
    showAccountFilter: Boolean = false,
    accounts: List<CloudAccount> = emptyList(),
    selectedAccountId: String? = null,
    onAccountSelected: (String?) -> Unit = {}
) {
    var showAccountMenu by remember { mutableStateOf(false) }

    val title = when (mode) {
        "recent" -> stringResource(R.string.nav_home)
        "starred" -> stringResource(R.string.nav_starred)
        "shared" -> stringResource(R.string.nav_shared)
        "trash" -> stringResource(R.string.nav_trash)
        else -> folderName
    }

    val icon = when (mode) {
        "recent" -> Icons.Default.AccessTime
        "starred" -> Icons.Default.Star
        "shared" -> Icons.Default.People
        "trash" -> Icons.Default.Delete
        "files" -> if (folderName == stringResource(R.string.nav_home)) Icons.Default.Home else Icons.Default.FolderOpen
        else -> Icons.Default.FolderOpen
    }
    
    val color = when (mode) {
        "recent" -> Color(0xFF4285F4)
        "starred" -> Color(0xFFFBC02D)
        "shared" -> Color(0xFF4CAF50)
        "trash" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        if (showAccountFilter && accounts.size > 1) {
            Box {
                IconButton(onClick = { showAccountMenu = true }) {
                    val currentAccount = accounts.find { it.id == selectedAccountId }
                    if (currentAccount != null) {
                        BrandIcon(providerId = currentAccount.provider.replace("_drive", ""), size = 24.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tune, 
                            contentDescription = stringResource(R.string.filter_all_accounts),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = { showAccountMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_all_accounts)) },
                        onClick = { 
                            onAccountSelected(null)
                            showAccountMenu = false 
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        },
                        trailingIcon = {
                            if (selectedAccountId == null) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(account.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(account.email ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            },
                            onClick = { 
                                onAccountSelected(account.id)
                                showAccountMenu = false 
                            },
                            leadingIcon = {
                                BrandIcon(providerId = account.provider.replace("_drive", ""), size = 20.dp)
                            },
                            trailingIcon = {
                                if (selectedAccountId == account.id) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: String,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    isOnline: Boolean = true
) {
    TopAppBar(
        title = { 
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_placeholder))
            }
            IconButton(onClick = onToggleView) {
                Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, null)
            }
            IconButton(
                onClick = onRefresh,
                enabled = isOnline
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    isSearchMode: Boolean = false,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true
) {
    Box(modifier = modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 4.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            color = if (isSearchMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = if (isSearchMode) 4.dp else 2.dp,
            border = if (isSearchMode) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        if (isSearchMode) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu, 
                        contentDescription = stringResource(if (isSearchMode) R.string.back else R.string.menu)
                    )
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
                if (!isSearchMode) {
                    IconButton(onClick = onToggleView) {
                        Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = if (isGridView) stringResource(R.string.view_list) else stringResource(R.string.view_grid))
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = isOnline
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, 
                            contentDescription = stringResource(R.string.sync),
                            tint = if (isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullFilePreview(file: CloudFile, viewModel: FilesViewModel, onBack: () -> Unit, onDownload: () -> Unit) {
    val context = LocalContext.current
    var showDetails by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        file.name, 
                        color = Color.White, 
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showDetails = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.info), tint = Color.White)
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val mimeType = file.mimeType.lowercase()
                val extension = file.name.substringAfterLast('.', "").lowercase()
                val localFile = viewModel.getLocalFile(file)
                
                val isImage = mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
                val isPdf = mimeType == "application/pdf" || extension == "pdf"
                
                val isTextFile = mimeType.startsWith("text/") || 
                                extension in listOf("kt", "java", "py", "js", "json", "xml", "html", "css", "sh", "bat", "sql", "txt", "log") ||
                                mimeType == "application/json" || mimeType == "application/xml" || mimeType == "application/javascript"
                
                val isMarkdown = extension == "md" || mimeType == "text/markdown"
                
                val isArchive = extension in listOf("zip", "rar", "7z", "tar", "gz") ||
                               mimeType in listOf("application/zip", "application/x-rar-compressed", "application/x-7z-compressed")

                val isAudioFile = mimeType.startsWith("audio/") || 
                                 extension in listOf("mp3", "wav", "m4a", "ogg", "flac", "aac")
                
                val isVideoFile = mimeType.startsWith("video/") || 
                                 extension in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")

                if (isImage) {
                    if (localFile != null || file.thumbnailLink != null) {
                        val model = if (localFile != null) {
                            localFile
                        } else {
                            file.thumbnailLink?.replace("=s220", "=s2048")
                        }
                        
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.image_preview_loading))
                    }
                } else if (isPdf) {
                    if (localFile != null) {
                        PdfPreviewScreen(file = localFile)
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.pdf_preview_loading))
                    }
                } else if (isTextFile) {
                    if (localFile != null) {
                        TextPreviewScreen(file = localFile)
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.text_preview_loading))
                    }
                } else if (isMarkdown) {
                    if (localFile != null) {
                        MarkdownPreviewScreen(file = localFile)
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.markdown_preview_loading))
                    }
                } else if (isArchive) {
                    if (localFile != null) {
                        ArchiveExplorerScreen(file = localFile)
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.archive_preview_loading))
                    }
                } else if (isAudioFile) {
                    if (localFile != null) {
                        AudioPreviewScreen(file = localFile)
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.audio_preview_loading))
                    }
                } else if (isVideoFile) {
                    if (localFile != null) {
                        VideoPreviewScreen(file = localFile)
                    } else {
                        PreviewLoadingState(file, viewModel, stringResource(R.string.video_preview_loading))
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp).fillMaxWidth()
                    ) {
                        FileIcon(file, size = 160.dp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.preview_not_supported), 
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = onDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Download, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.download))
                            }
                            val provider = file.provider.lowercase()
                            val isCloudWebSupported = provider == "google_drive" || provider == "onedrive" || provider == "dropbox" || provider == "box"
                            
                            if (file.webViewLink != null && isCloudWebSupported) {
                                OutlinedButton(
                                    onClick = { 
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file.webViewLink!!))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.open_in_web))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        FileDetailsDialog(file = file, onDismiss = { showDetails = false })
    }
}

@Composable
fun PreviewLoadingState(file: CloudFile, viewModel: FilesViewModel, loadingText: String) {
    val transfer by viewModel.getTransferProgress(file.id).collectAsState(null)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp).fillMaxWidth()
    ) {
        if (transfer != null) {
            LinearProgressIndicator(
                progress = { transfer!!.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${(transfer!!.progress * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(loadingText, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        if (transfer == null) {
            Button(onClick = { viewModel.downloadFile(file) }) {
                Text(stringResource(R.string.download_to_preview))
            }
        }
    }
}

@Composable
fun FileDetailsDialog(file: CloudFile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(R.string.detail_name), file.name)
                DetailRow(stringResource(R.string.detail_type), file.mimeType.split("/").last().uppercase())
                if (file.size != null) DetailRow(stringResource(R.string.detail_size), formatSize(file.size))
                DetailRow(stringResource(R.string.detail_owner), file.ownerDisplayName ?: file.providerDisplayName ?: file.provider)
                if (file.modifiedTime != null) {
                    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.modifiedTime))
                    DetailRow(stringResource(R.string.detail_modified), date)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun BreadcrumbsRow(path: List<FolderInfo>, onNavigate: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(path) { index, folder ->
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (index == path.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable { onNavigate(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (index < path.size - 1) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualTopAppBar(
    selectedCount: Int,
    showShare: Boolean,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
        actions = {
            if (showShare) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                }
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
fun FileList(
    files: List<CloudFile>,
    selectedIds: Set<String>,
    onClick: (CloudFile) -> Unit,
    onLongClick: (CloudFile) -> Unit,
    onMoreClick: (CloudFile) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)) {
        items(files, key = { it.id + it.accountId }) { file ->
            val isSelected = selectedIds.contains(file.id)
            FileListItem(
                file = file,
                isSelected = isSelected,
                onClick = { onClick(file) },
                onLongClick = { onLongClick(file) },
                onMoreClick = { onMoreClick(file) }
            )
        }
    }
}

@Composable
fun FileGrid(
    files: List<CloudFile>,
    selectedIds: Set<String>,
    onClick: (CloudFile) -> Unit,
    onLongClick: (CloudFile) -> Unit,
    onMoreClick: (CloudFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(files, key = { it.id + it.accountId }) { file ->
            val isSelected = selectedIds.contains(file.id)
            FileGridItem(
                file = file,
                isSelected = isSelected,
                onClick = { onClick(file) },
                onLongClick = { onLongClick(file) },
                onMoreClick = { onMoreClick(file) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: CloudFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    text = file.name, 
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                ) 
            },
            supportingContent = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallProviderBadge(file)
                }
            },
            leadingContent = {
                Box {
                    FileIcon(file)
                    if (isSelected) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(18.dp).align(Alignment.BottomEnd)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: CloudFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(Alignment.Center).padding(vertical = 8.dp)) {
                    FileIcon(file, size = 72.dp)
                    if (isSelected) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp).align(Alignment.BottomEnd)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
                IconButton(
                    onClick = onMoreClick, 
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = file.name, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            SmallProviderBadge(file)
        }
    }
}

@Composable
fun SmallProviderBadge(file: CloudFile) {
    val providerId = file.provider.lowercase()
    val logoRes = when {
        providerId.contains("google", true) -> R.drawable.ic_google_drive
        providerId.contains("onedrive", true) -> R.drawable.ic_onedrive
        providerId.contains("dropbox", true) -> R.drawable.ic_dropbox
        providerId.contains("box", true) -> R.drawable.ic_box
        else -> null
    }

    val color = when {
        providerId.contains("google", true) -> Color(0xFF4285F4)
        providerId.contains("onedrive", true) -> Color(0xFF0078D4)
        providerId.contains("dropbox", true) -> Color(0xFF0061FF)
        providerId.contains("box", true) -> Color(0xFF0061D5)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logoRes != null) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = file.ownerDisplayName ?: file.providerDisplayName ?: file.provider,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileIcon(file: CloudFile, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val mimeType = file.mimeType
    val isFolder = file.isFolder
    val isImageOrVideo = mimeType.startsWith("image/") || mimeType.startsWith("video/")
    
    val iconColor = FileIconHelper.getColorForMimeType(mimeType, isFolder, file.name)
    val iconVector = FileIconHelper.getIconForMimeType(mimeType, isFolder, file.name)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = iconColor.copy(alpha = 0.15f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isImageOrVideo && file.thumbnailLink != null && !isFolder) {
                AsyncImage(
                    model = file.thumbnailLink,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            
            // Shared Badge
            if (file.isShared || file.isPublic) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(size * 0.4f)
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (file.isPublic) Icons.Default.Public else Icons.Default.People,
                            contentDescription = "Shared",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(size * 0.25f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActionBottomSheet(
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onUploadFiles: () -> Unit,
    onUploadFolder: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(stringResource(R.string.create_upload_title), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(
                headlineContent = { Text(stringResource(R.string.new_folder)) },
                leadingContent = { Icon(Icons.Default.CreateNewFolder, null) },
                modifier = Modifier.clickable { onDismiss(); onCreateFolder() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.upload_files)) },
                leadingContent = { Icon(Icons.Default.UploadFile, null) },
                modifier = Modifier.clickable { onDismiss(); onUploadFiles() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.upload_folder)) },
                leadingContent = { Icon(Icons.Default.DriveFolderUpload, null) },
                modifier = Modifier.clickable { onDismiss(); onUploadFolder() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionBottomSheet(file: CloudFile, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FileIcon(file, size = 40.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(file.ownerInfo ?: file.provider, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            ActionItem(Icons.Default.Download, stringResource(R.string.download)) { onAction("download") }
            ActionItem(Icons.Default.Edit, stringResource(R.string.rename)) { onAction("rename") }
            
            ActionItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_native)) { onAction("open_native") }

            val provider = file.provider.lowercase()
            val isCloudWebSupported = provider == "google_drive" || provider == "onedrive" || provider == "dropbox" || provider == "box"
            
            ActionItem(
                icon = Icons.Default.Share, 
                label = stringResource(R.string.share),
                enabled = isCloudWebSupported && !file.isTrashed,
                onClick = { onAction("share") }
            )

            if (file.webViewLink != null && isCloudWebSupported) {
                ActionItem(Icons.Default.Language, stringResource(R.string.open_in_web)) { onAction("web") }
            }


            ActionItem(Icons.Default.Info, stringResource(R.string.details)) { onAction("details") }
            ActionItem(Icons.Default.Delete, stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) { onAction("delete") }
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector, 
    label: String, 
    color: Color = MaterialTheme.colorScheme.onSurface, 
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                label, 
                color = if (enabled) color else color.copy(alpha = 0.38f)
            ) 
        },
        leadingContent = { 
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (enabled) color else color.copy(alpha = 0.38f)
            ) 
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
fun CreateFolderDialog(value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_folder_title)) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(stringResource(R.string.folder_name_label)) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LoadingShimmerList() {
    Column(modifier = Modifier.padding(16.dp)) {
        repeat(8) {
            Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp)))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Box(modifier = Modifier.width(180.dp).height(18.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.width(100.dp).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
fun EmptyState(isSearch: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (isSearch) Icons.Default.SearchOff else Icons.Default.CloudQueue, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Text(if (isSearch) stringResource(R.string.no_results) else stringResource(R.string.empty_files), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
        if (!isSearch) Text(stringResource(R.string.connect_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
