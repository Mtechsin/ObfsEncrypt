package com.obfs.encrypt.crypto

import com.obfs.encrypt.R

enum class EncryptionMethod(
    val displayNameRes: Int,
    val descriptionRes: Int,
    val speedLabelRes: Int,
    val tCostInIterations: Int,
    val mCostInKibibyte: Int,
    val parallelism: Int,
    val detailedExplanationRes: Int,
    val estimatedTimePerGbRes: Int,
    val isRecommended: Boolean = false,
    val memoryRequirementRes: Int,
    val cpuRequirementRes: Int,
    val warningMessageRes: Int? = null,
    // Keep legacy fields for backward compatibility if needed, but ideally we should migrate all usage
    val displayName: String = "",
    val description: String = "",
    val speedLabel: String = "",
    val detailedExplanation: String = "",
    val estimatedTimePerGb: String = "",
    val memoryRequirement: String = "",
    val cpuRequirement: String = "",
    val warningMessage: String? = null
) {
    FAST(
        displayNameRes = R.string.method_fast_name,
        descriptionRes = R.string.method_fast_desc,
        speedLabelRes = R.string.method_fast_speed,
        tCostInIterations = 1,
        mCostInKibibyte = 8 * 1024,
        parallelism = 1,
        detailedExplanationRes = R.string.method_fast_detail,
        estimatedTimePerGbRes = R.string.method_fast_time,
        isRecommended = false,
        memoryRequirementRes = R.string.method_fast_memory,
        cpuRequirementRes = R.string.method_fast_cpu
    ),
    STANDARD(
        displayNameRes = R.string.method_standard_name,
        descriptionRes = R.string.method_standard_desc,
        speedLabelRes = R.string.method_standard_speed,
        tCostInIterations = 4,
        mCostInKibibyte = 64 * 1024,
        parallelism = 1,
        detailedExplanationRes = R.string.method_standard_detail,
        estimatedTimePerGbRes = R.string.method_standard_time,
        isRecommended = true,
        memoryRequirementRes = R.string.method_standard_memory,
        cpuRequirementRes = R.string.method_standard_cpu
    ),
    STRONG(
        displayNameRes = R.string.method_strong_name,
        descriptionRes = R.string.method_strong_desc,
        speedLabelRes = R.string.method_strong_speed,
        tCostInIterations = 8,
        mCostInKibibyte = 128 * 1024,
        parallelism = 2,
        detailedExplanationRes = R.string.method_strong_detail,
        estimatedTimePerGbRes = R.string.method_strong_time,
        isRecommended = false,
        memoryRequirementRes = R.string.method_strong_memory,
        cpuRequirementRes = R.string.method_strong_cpu,
        warningMessageRes = R.string.method_strong_warning
    )
}
