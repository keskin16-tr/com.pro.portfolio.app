package com.portfolio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AssetEntity::class,
        TradeEntity::class,
        DividendEntity::class,
        PortfolioSnapshotEntity::class,
        AssetPriceSnapshotEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun tradeDao(): TradeDao
    abstract fun dividendDao(): DividendDao
    abstract fun portfolioSnapshotDao(): PortfolioSnapshotDao
    abstract fun assetPriceSnapshotDao(): AssetPriceSnapshotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    assetId INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice REAL NOT NULL,
                    fee REAL NOT NULL,
                    realizedProfit REAL NOT NULL,
                    executedAt INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_assetId ON transactions(assetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_executedAt ON transactions(executedAt)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS dividends (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    assetId INTEGER NOT NULL,
                    ticker TEXT NOT NULL,
                    quantityAtPayment REAL NOT NULL,
                    grossAmount REAL NOT NULL,
                    withholdingTax REAL NOT NULL,
                    netAmount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    exDate INTEGER NOT NULL,
                    paymentDate INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dividends_assetId ON dividends(assetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dividends_paymentDate ON dividends(paymentDate)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS portfolio_snapshots (
                    snapshotDate INTEGER NOT NULL PRIMARY KEY,
                    marketValue REAL NOT NULL,
                    investedValue REAL NOT NULL,
                    assetCount INTEGER NOT NULL
                )""")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN broker TEXT NOT NULL DEFAULT 'Belirtilmedi'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN openingQuantity REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE assets ADD COLUMN openingCostPrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("""UPDATE assets SET openingQuantity = quantity, openingCostPrice = costPrice WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE transactions.assetId = assets.id)""")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN priceSource TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'TRY'")
                db.execSQL("UPDATE transactions SET currency = COALESCE((SELECT currency FROM assets WHERE assets.id = transactions.assetId), 'TRY')")
                db.execSQL("ALTER TABLE dividends ADD COLUMN tradeId INTEGER")
                db.query("SELECT id, assetId, grossAmount, withholdingTax, currency, paymentDate, note FROM dividends").use { cursor ->
                    while (cursor.moveToNext()) {
                        val dividendId = cursor.getLong(0)
                        val assetId = cursor.getLong(1)
                        val gross = cursor.getDouble(2)
                        val tax = cursor.getDouble(3)
                        val currency = cursor.getString(4) ?: "TRY"
                        val paymentDate = cursor.getLong(5)
                        val note = cursor.getString(6) ?: "Temettü ödemesi"
                        val statement = db.compileStatement(
                            "INSERT INTO transactions (assetId, action, quantity, unitPrice, fee, realizedProfit, executedAt, note, currency) VALUES (?, 'DIVIDEND', 1.0, ?, ?, 0.0, ?, ?, ?)"
                        )
                        statement.bindLong(1, assetId)
                        statement.bindDouble(2, gross)
                        statement.bindDouble(3, tax)
                        statement.bindLong(4, paymentDate)
                        statement.bindString(5, note)
                        statement.bindString(6, currency)
                        val tradeId = statement.executeInsert()
                        db.execSQL("UPDATE dividends SET tradeId = ? WHERE id = ?", arrayOf(tradeId, dividendId))
                    }
                }
                db.execSQL("""CREATE TABLE IF NOT EXISTS asset_price_snapshots (
                    assetId INTEGER NOT NULL,
                    snapshotDate INTEGER NOT NULL,
                    price REAL NOT NULL,
                    currency TEXT NOT NULL,
                    PRIMARY KEY(assetId, snapshotDate),
                    FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_price_snapshots_snapshotDate ON asset_price_snapshots(snapshotDate)")
            }
        }


        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN applied INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE assets ADD COLUMN createdAt INTEGER")
                db.execSQL("ALTER TABLE assets ADD COLUMN updatedAt INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN createdAt INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN realizedProfitTry REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "portfolio_local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build().also { INSTANCE = it }
        }
    }
}
