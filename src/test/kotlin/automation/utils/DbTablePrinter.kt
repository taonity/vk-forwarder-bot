package automation.utils

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class DbTablePrinter (
    private val jdbcTemplate: JdbcTemplate
) {
    fun print(tableName: String?): String {
        val tableData: List<Map<String, Any>> = jdbcTemplate.queryForList(String.format("SELECT * FROM %s", tableName))
        return formatAsTable(tableData)
    }

    private fun formatAsTable(data: List<Map<String, Any>>): String {
        if (data.isEmpty()) {
            return "No data found."
        }

        val columnNames = getColumnNames(data[0])
        val columnWidths = getColumnWidths(data, columnNames)

        val table = StringBuilder()

        // Build the table header
        table.append("+")
        for (width in columnWidths) {
            table.append(String.format("%-" + (width + 2) + "s+", "").replace(' ', '-'))
        }
        table.append("\n")

        table.append("|")
        for (i in columnNames.indices) {
            table.append(String.format(" %-" + columnWidths[i] + "s |", columnNames[i]))
        }
        table.append("\n")

        // Build the separator line
        table.append("+")
        for (width in columnWidths) {
            table.append(String.format("%-" + (width + 2) + "s+", "").replace(' ', '-'))
        }
        table.append("\n")

        // Build the table rows
        for (row in data) {
            table.append("|")
            for (i in columnNames.indices) {
                table.append(String.format(" %-" + columnWidths[i] + "s |", row[columnNames[i]]))
            }
            table.append("\n")
        }

        // Build the final separator line
        table.append("+")
        for (width in columnWidths) {
            table.append(String.format("%-" + (width + 2) + "s+", "").replace(' ', '-'))
        }

        return table.toString()
    }

    private fun getColumnNames(row: Map<String, Any>): List<String> {
        val columnNames: MutableList<String> = ArrayList()
        for ((key) in row) {
            columnNames.add(key)
        }
        return columnNames
    }

    private fun getColumnWidths(data: List<Map<String, Any>>, columnNames: List<String>): List<Int> {
        val columnWidths: MutableList<Int> = ArrayList()

        for (columnName in columnNames) {
            var maxWidth = columnName.length
            for (row in data) {
                val value = row[columnName]
                if (value != null) {
                    val valueLength = value.toString().length
                    if (valueLength > maxWidth) {
                        maxWidth = valueLength
                    }
                }
            }
            columnWidths.add(maxWidth)
        }

        return columnWidths
    }
}
