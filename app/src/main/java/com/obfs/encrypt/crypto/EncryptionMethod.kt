package com.obfs.encrypt.crypto

enum class EncryptionMethod(
    val displayName: String,
    val description: String,
    val speedLabel: String,
    val tCostInIterations: Int,
    val mCostInKibibyte: Int,
    val parallelism: Int
) {
    FAST(
        displayName = "Fast",
        description = "Quick encryption for large files. Lower security.",
        speedLabel = "Fastest",
        tCostInIterations = 1,
        mCostInKibibyte = 8 * 1024,
        parallelism = 1
    ),
    STANDARD(
        displayName = "Standard",
        description = "Balanced security and speed. Recommended.",
        speedLabel = "Fast",
        tCostInIterations = 4,
        mCostInKibibyte = 64 * 1024,
        parallelism = 1
    ),
    STRONG(
        displayName = "Strong",
        description = "Maximum security. Slower but more resistant.",
        speedLabel = "Slow",
        tCostInIterations = 8,
        mCostInKibibyte = 128 * 1024,
        parallelism = 2
    )
}
