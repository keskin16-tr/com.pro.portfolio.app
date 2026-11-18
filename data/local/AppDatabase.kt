package com.portfolio.app.data.local
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities=[AssetEntity::class,TradeEntity::class],version=2,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){
 abstract fun assetDao():AssetDao
 abstract fun tradeDao():TradeDao
 companion object{
  @Volatile private var INSTANCE:AppDatabase?=null
  private val MIGRATION_1_2=object:Migration(1,2){
   override fun migrate(db:SupportSQLiteDatabase){
    db.execSQL("""CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      assetId INTEGER NOT NULL, action TEXT NOT NULL, quantity REAL NOT NULL,
      unitPrice REAL NOT NULL, fee REAL NOT NULL, realizedProfit REAL NOT NULL,
      executedAt INTEGER NOT NULL, note TEXT NOT NULL,
      FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_assetId ON transactions(assetId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_executedAt ON transactions(executedAt)")
   }
  }
  fun getDatabase(context:Context):AppDatabase=INSTANCE?: synchronized(this){
   INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"portfolio_local.db")
    .addMigrations(MIGRATION_1_2).build().also{INSTANCE=it}
  }
 }
}
