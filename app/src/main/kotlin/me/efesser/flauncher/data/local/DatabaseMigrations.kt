package me.efesser.flauncher.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_2_7 = object : Migration(2, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")

            migrateAppsTable(db)
            migrateCategoriesTable(db)
            migrateAppsCategoriesTable(db)
            ensureLauncherSpacersTable(db)

            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    private fun migrateAppsTable(db: SupportSQLiteDatabase) {
        val columns = tableColumns(db, "apps")
        if (columns.isEmpty()) return

        val packageColumn = when {
            "package_name" in columns -> "package_name"
            "packageName" in columns -> "packageName"
            else -> return
        }
        val hasSideloaded = "sideloaded" in columns
        val needsRebuild = packageColumn != "package_name" || hasSideloaded

        if (!needsRebuild) return

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS apps_migrated (
                package_name TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                version TEXT NOT NULL,
                hidden INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO apps_migrated (package_name, name, version, hidden)
            SELECT $packageColumn, name, version, hidden FROM apps
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE apps")
        db.execSQL("ALTER TABLE apps_migrated RENAME TO apps")
    }

    private fun migrateCategoriesTable(db: SupportSQLiteDatabase) {
        val columns = tableColumns(db, "categories")
        if (columns.isEmpty()) return

        val needsRebuild = "row_height" !in columns || "columns_count" !in columns
        if (!needsRebuild) return

        val rowHeightColumn = if ("rowHeight" in columns) "rowHeight" else "110"
        val columnsCountColumn = if ("columnsCount" in columns) "columnsCount" else "6"

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories_migrated (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                sort INTEGER NOT NULL DEFAULT 0,
                type INTEGER NOT NULL DEFAULT 0,
                row_height INTEGER NOT NULL DEFAULT 110,
                columns_count INTEGER NOT NULL DEFAULT 6,
                `order` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO categories_migrated
                (id, name, sort, type, row_height, columns_count, `order`)
            SELECT id, name, sort, type, $rowHeightColumn, $columnsCountColumn, `order`
            FROM categories
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE categories")
        db.execSQL("ALTER TABLE categories_migrated RENAME TO categories")
    }

    private fun migrateAppsCategoriesTable(db: SupportSQLiteDatabase) {
        val columns = tableColumns(db, "apps_categories")
        if (columns.isEmpty()) return

        if ("category_id" in columns && "app_package_name" in columns) return

        val categoryColumn = when {
            "category_id" in columns -> "category_id"
            "categoryId" in columns -> "categoryId"
            else -> return
        }
        val packageColumn = when {
            "app_package_name" in columns -> "app_package_name"
            "appPackageName" in columns -> "appPackageName"
            else -> return
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS apps_categories_migrated (
                category_id INTEGER NOT NULL,
                app_package_name TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                PRIMARY KEY(category_id, app_package_name)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO apps_categories_migrated
                (category_id, app_package_name, `order`)
            SELECT $categoryColumn, $packageColumn, `order`
            FROM apps_categories
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE apps_categories")
        db.execSQL("ALTER TABLE apps_categories_migrated RENAME TO apps_categories")
    }

    private fun ensureLauncherSpacersTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS launcher_spacers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                height INTEGER NOT NULL,
                `order` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) {
                    columns.add(cursor.getString(nameIndex))
                }
            }
        }
        return columns
    }
}
