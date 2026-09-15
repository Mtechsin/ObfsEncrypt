package com.obfs.encrypt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.obfs.encrypt.R
import com.obfs.encrypt.diagnostics.BugReportExporter
import com.obfs.encrypt.diagnostics.DiagnosticsCollector
import com.obfs.encrypt.viewmodel.BugReportViewModel
import com.obfs.encrypt.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explains what the diagnostic report includes, lets the user add a short summary,
 * then share or copy a sanitized markdown report. No network is used.
 */
@Composable
fun BugReportDialog(
    viewModel: MainViewModel,
    bugReportViewModel: BugReportViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val collector = bugReportViewModel.collector
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val secureDelete by viewModel.secureDeleteOriginals.collectAsState()
    val outputUri by viewModel.currentOutputUri.collectAsState()

    fun buildAnd(action: (String) -> Unit) {
        if (building) return
        building = true
        lastError = null
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    collector.buildReport(
                        context = context.applicationContext,
                        userSummary = summary,
                        settings = DiagnosticsCollector.SettingsSnapshot(
                            themeMode = themeMode.name,
                            language = language,
                            appLockEnabled = appLockEnabled,
                            biometricEnabled = null,
                            secureDelete = secureDelete,
                            integrityCheck = null,
                            customOutputFolder = outputUri != null
                        )
                    )
                }
                action(report)
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            } finally {
                building = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!building) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.report_a_bug)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.bug_report_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.bug_report_includes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.bug_report_summary_label)) },
                    placeholder = { Text(stringResource(R.string.bug_report_summary_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                if (lastError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bug_report_build_failed, lastError ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (building) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = { buildAnd { BugReportExporter.share(context, it) } },
                    enabled = !building
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.bug_report_share))
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { buildAnd { BugReportExporter.copy(context, it) } },
                    enabled = !building
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.bug_report_copy))
                }
                TextButton(onClick = onDismiss, enabled = !building) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

// Secondary entry used from HelpScreen (settings snapshot left null).
@Composable
fun BugReportDialog(
    bugReportViewModel: BugReportViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val collector = bugReportViewModel.collector
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf("") }
    var building by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!building) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.report_a_bug)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.bug_report_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.bug_report_summary_label)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (building) return@Button
                    building = true
                    scope.launch {
                        try {
                            val report = withContext(Dispatchers.IO) {
                                collector.buildReport(context.applicationContext, summary)
                            }
                            BugReportExporter.share(context, report)
                        } finally {
                            building = false
                        }
                    }
                },
                enabled = !building
            ) {
                Text(stringResource(R.string.bug_report_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !building) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
