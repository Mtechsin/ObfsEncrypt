package com.obfs.encrypt.viewmodel

import androidx.lifecycle.ViewModel
import com.obfs.encrypt.diagnostics.DiagnosticsCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BugReportViewModel @Inject constructor(
    val collector: DiagnosticsCollector
) : ViewModel()
