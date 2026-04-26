package org.wit.vitasense.model

enum class MoodGroup {
    POSITIVE,
    NEGATIVE,
}

enum class MoodType(
    val displayName: String,
    val group: MoodGroup,
) {
    CALM("Calm", MoodGroup.POSITIVE),
    HAPPY("Happy", MoodGroup.POSITIVE),
    RELAXED("Relaxed", MoodGroup.POSITIVE),
    ANXIOUS("Anxious", MoodGroup.NEGATIVE),
    LOW("Low", MoodGroup.NEGATIVE),
    TIRED("Tired", MoodGroup.NEGATIVE),
}
