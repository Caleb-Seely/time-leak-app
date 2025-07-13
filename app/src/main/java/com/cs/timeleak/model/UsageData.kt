package com.cs.timeleak.model

import java.util.Date

data class UsageData(
    val userId: String = "",
    val phoneNumber: String = "",
    val date: Date = Date(),
    val totalScreenTime: Long = 0,
    val socialMediaTime: Long = 0,
    val entertainmentTime: Long = 0,
    val goalTime: Long = 0
)

data class AppUsageData(
    val packageName: String = "",
    val usageTimeMillis: Long = 0,
    val appName: String = "",
    val category: String = ""
) 