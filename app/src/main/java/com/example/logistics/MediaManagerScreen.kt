package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // <--- This fixes the "Int" mismatch error
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState // <--- Fixed by the dependency update
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaManagerScreen(navController: NavController) {
    val context = LocalContext.current

    // observeAsState will now work because of the gradle dependency
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagLiveData("trip_upload")
        .observeAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Status") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (workInfos.isEmpty()) {
                Text("No recent uploads found.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // This 'items' call now works because we imported androidx.compose.foundation.lazy.items
                    items(workInfos.reversed()) { workInfo ->
                        UploadStatusCard(workInfo)
                    }
                }
            }
        }
    }
}

@Composable
fun UploadStatusCard(workInfo: WorkInfo) {
    val state = workInfo.state

    val color = when (state) {
        WorkInfo.State.SUCCEEDED -> Color(0xFF4CAF50) // Green
        WorkInfo.State.FAILED -> MaterialTheme.colorScheme.error
        WorkInfo.State.RUNNING -> MaterialTheme.colorScheme.primary
        WorkInfo.State.ENQUEUED -> Color.Gray
        else -> Color.Gray
    }

    val icon = when (state) {
        WorkInfo.State.SUCCEEDED -> Icons.Filled.CheckCircle
        WorkInfo.State.FAILED -> Icons.Filled.Error
        else -> Icons.Filled.CloudUpload
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Upload Task", style = MaterialTheme.typography.titleMedium)
                Text("Status: $state", style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}