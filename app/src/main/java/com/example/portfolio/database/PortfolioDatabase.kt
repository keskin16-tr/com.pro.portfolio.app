
package com.example.portfolio.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.portfolio.model.PortfolioAsset
import com.example.portfolio.model.PortfolioTransaction

class PortfolioDatabase(context: Context) :
    SQLiteOpenHelper(context, "portfolio.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE assets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                quantity REAL NOT NULL DEFAULT 0,
                average_cost REAL NOT NULL DEFAULT 0,
                current_price REAL NOT NULL DEFAULT 0,
                currency TEXT NOT NULL DEFAULT 'TRY'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                asset_id INTEGER NOT NULL,
                symbol TEXT NOT NULL,
                type TEXT NOT NULL,
                quantity REAL NOT NULL,
                price REAL NOT NULL,
                commission REAL NOT NULL DEFAULT 0,
                date INTEGER NOT NULL,
                FOREIGN KEY(asset_id) REFERENCES assets(id)
            )
        """.trimIndent())
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {}

    fun getAssets(): List<PortfolioAsset> {
        val result = mutableListOf<PortfolioAsset>()
        readableDatabase.rawQuery(
            "SELECT * FROM assets ORDER BY name", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    PortfolioAsset(
                        id = cursor.getLong(0),
                        symbol = cursor.getString(1),
                        name = cursor.getString(2),
                        type = cursor.getString(3),
                        quantity = cursor.getDouble(4),
                        averageCost = cursor.getDouble(5),
                        currentPrice = cursor.getDouble(6),
                        currency = cursor.getString(7)
                    )
                )
            }
        }
        return result
    }

    fun addAsset(
        symbol: String,
        name: String,
        type: String,
        currency: String = "TRY"
    ): Long {
        val values = ContentValues().apply {
            put("symbol", symbol.uppercase())
            put("name", name)
            put("type", type)
            put("currency", currency)
        }

        return writableDatabase.insertWithOnConflict(
            "assets", null, values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun updatePrice(symbol: String, price: Double) {
        if (!price.isFinite() || price <= 0) return

        val values = ContentValues().apply {
            put("current_price", price)
        }

        writableDatabase.update(
            "assets", values, "symbol=?",
            arrayOf(symbol.uppercase())
        )
    }

    fun addTransaction(
        symbol: String,
        type: String,
        quantity: Double,
        price: Double,
        commission: Double
    ): Boolean {
        if (!quantity.isFinite() || !price.isFinite() ||
            !commission.isFinite() ||
            quantity <= 0 || price <= 0 || commission < 0
        ) return false

        val db = writableDatabase
        db.beginTransaction()

        try {
            val asset = db.rawQuery(
                "SELECT id, quantity, average_cost FROM assets WHERE symbol=?",
                arrayOf(symbol.uppercase())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return false
                Triple(
                    cursor.getLong(0),
                    cursor.getDouble(1),
                    cursor.getDouble(2)
                )
            }

            val id = asset.first
            val oldQty = asset.second
            val oldCost = asset.third

            val newQty: Double
            val newCost: Double

            if (type == "BUY") {
                val total = oldQty * oldCost +
                    quantity * price + commission

                newQty = oldQty + quantity
                newCost = total / newQty
            } else if (type == "SELL") {
                if (quantity > oldQty) return false

                newQty = oldQty - quantity
                newCost = if (newQty > 0) oldCost else 0.0
            } else {
                return false
            }

            val transaction = ContentValues().apply {
                put("asset_id", id)
                put("symbol", symbol.uppercase())
                put("type", type)
                put("quantity", quantity)
                put("price", price)
                put("commission", commission)
                put("date", System.currentTimeMillis())
            }

            db.insertOrThrow(
                "transactions", null, transaction
            )

            val update = ContentValues().apply {
                put("quantity", newQty)
                put("average_cost", newCost)
            }

            db.update(
                "assets", update, "id=?",
                arrayOf(id.toString())
            )

            db.setTransactionSuccessful()
            return true

        } catch (e: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }

    fun getTransactions(): List<PortfolioTransaction> {
        val result = mutableListOf<PortfolioTransaction>()

        readableDatabase.rawQuery(
            "SELECT * FROM transactions ORDER BY date DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    PortfolioTransaction(
                        id = c.getLong(0),
                        assetId = c.getLong(1),
                        symbol = c.getString(2),
                        type = c.getString(3),
                        quantity = c.getDouble(4),
                        price = c.getDouble(5),
                        commission = c.getDouble(6),
                        date = c.getLong(7)
                    )
                )
            }
        }
        return result
    }

    fun deleteAsset(id: Long): Boolean {
        val db = writableDatabase
        val hasTransactions = db.rawQuery(
            "SELECT COUNT(*) FROM transactions WHERE asset_id=?",
            arrayOf(id.toString())
        ).use {
            it.moveToFirst()
            it.getInt(0) > 0
        }

        if (hasTransactions) return false

        return db.delete(
            "assets", "id=?", arrayOf(id.toString())
        ) > 0
    }
}
