package org.wit.vitasense.model

data class DemoBundleInfo(
    val id: String,
    val title: String,
    val description: String,
)

enum class ImportStatus {
    SUCCESS,
    PARTIAL_FAILED,
    FAILED,
}

data class ImportOperationResult(
    val status: ImportStatus,
    val message: String,
    val rawCount: Int,
    val insertedCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
)

data class MoodFilter(
    val group: MoodGroup? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)
