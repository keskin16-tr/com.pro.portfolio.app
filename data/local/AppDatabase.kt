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
        PortfolioSnapshotEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun tradeDao(): TradeDao
    abstract fun dividendDao(): DividendDao
    abstract fun portfolioSnapshotDao(): PortfolioSnapshotDao

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

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "portfolio_local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { INSTANCE = it }
        }
    }
}
