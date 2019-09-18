package org.jetbrains.bunches.idea.actions

import com.intellij.openapi.project.Project
import java.util.*
import javax.swing.SpinnerNumberModel

class CommitFilterDialogKt(project: Project) : CommitFilterDialog(project) {
    init {
        title = "Commit Filter Bunches"
        amountValue.model = SpinnerNumberModel(
            Integer.valueOf(5),
            Integer.valueOf(0),
            null,
            Integer.valueOf(1)
        )
        dateValue.model = SpinnerNumberModel(
            Integer.valueOf(2),
            Integer.valueOf(0),
            null,
            Integer.valueOf(1)
        )
    }

    fun getParameters(): CommitFilterParameters {
        return CommitFilterParameters(
            if (dateCheckBox.isSelected)
                countDate(dateValue.model.value as Int, dateTypeComboBox.selectedItem as String)
            else null,
            if (amountCheckBox.isSelected)
                amountValue.model.value as Int
            else null
        )
    }

    private fun countDate(amount: Int, dateType: String): Date {
        val calendarDateType = getCalendarDateType(dateType)
        val calendar = Calendar.getInstance()
        calendar.add(calendarDateType, -amount)
        return calendar.time
    }

    private fun getCalendarDateType(dateType: String): Int =
        when(dateType) {
            "week" -> Calendar.WEEK_OF_YEAR
            "day" -> Calendar.DAY_OF_YEAR
            "month" -> Calendar.MONTH
            "year" -> Calendar.YEAR
            else -> -1
        }
}