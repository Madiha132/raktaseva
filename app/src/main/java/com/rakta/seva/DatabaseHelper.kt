package com.example.raktaseva

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "RaktaSeva.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS donors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                blood_group TEXT NOT NULL,
                location TEXT NOT NULL,
                age INTEGER NOT NULL,
                registered_date TEXT NOT NULL,
                available INTEGER DEFAULT 1
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                blood_group TEXT NOT NULL,
                hospital TEXT NOT NULL,
                units INTEGER NOT NULL,
                contact TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                donor_coming INTEGER DEFAULT 0
            )"""
        )

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS donation_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                donor_name TEXT NOT NULL,
                blood_group TEXT NOT NULL,
                donation_date TEXT NOT NULL,
                location TEXT NOT NULL
            )"""
        )

        // Sample donors
        insertSample(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS donors")
        db.execSQL("DROP TABLE IF EXISTS alerts")
        db.execSQL("DROP TABLE IF EXISTS donation_history")
        onCreate(db)
    }

    private fun insertSample(db: SQLiteDatabase) {
        val donors = listOf(
            arrayOf("Ravi Kumar", "9876543210", "O+", "Hubli", "25", "10 May 2026"),
            arrayOf("Priya Sharma", "9876501234", "A+", "Dharwad", "24", "08 May 2026"),
            arrayOf("Arjun Nayak", "9845670001", "B+", "Belgaum", "30", "05 May 2026"),
            arrayOf("Meena Patil", "9845670002", "AB-", "Gadag", "28", "01 May 2026"),
            arrayOf("Suresh Bhat", "9845670003", "O-", "Hubli", "35", "28 Apr 2026")
        )
        donors.forEach { d ->
            val cv = ContentValues()
            cv.put("name", d[0]); cv.put("phone", d[1]); cv.put("blood_group", d[2])
            cv.put("location", d[3]); cv.put("age", d[4].toInt()); cv.put("registered_date", d[5])
            cv.put("available", 1)
            db.insert("donors", null, cv)
        }
    }

    // ---- DONORS ----
    fun insertDonor(
        name: String, phone: String, blood: String,
        location: String, age: Int, date: String
    ): Long {
        val cv = ContentValues()
        cv.put("name", name); cv.put("phone", phone); cv.put("blood_group", blood)
        cv.put("location", location); cv.put("age", age); cv.put("registered_date", date)
        cv.put("available", 1)
        return writableDatabase.insert("donors", null, cv)
    }

    fun getDonors(filter: String? = null): List<DonorRow> {
        val list = mutableListOf<DonorRow>()
        val q = if (filter == null)
            "SELECT * FROM donors ORDER BY id DESC"
        else
            "SELECT * FROM donors WHERE blood_group='$filter' ORDER BY id DESC"
        val c = readableDatabase.rawQuery(q, null)
        while (c.moveToNext()) {
            list.add(
                DonorRow(
                    c.getInt(0), c.getString(1), c.getString(2),
                    c.getString(3), c.getString(4), c.getInt(5),
                    c.getString(6), c.getInt(7) == 1
                )
            )
        }
        c.close()
        return list
    }

    fun getDonorCount(): Int {
        val c = readableDatabase.rawQuery("SELECT COUNT(*) FROM donors", null)
        val count = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return count
    }

    // ---- ALERTS ----
    fun insertAlert(blood: String, hospital: String, units: Int, contact: String, ts: String): Long {
        val cv = ContentValues()
        cv.put("blood_group", blood); cv.put("hospital", hospital)
        cv.put("units", units); cv.put("contact", contact)
        cv.put("timestamp", ts); cv.put("donor_coming", 0)
        return writableDatabase.insert("alerts", null, cv)
    }

    fun getAlerts(): List<AlertRow> {
        val list = mutableListOf<AlertRow>()
        val c = readableDatabase.rawQuery("SELECT * FROM alerts ORDER BY id DESC", null)
        while (c.moveToNext()) {
            list.add(
                AlertRow(
                    c.getInt(0), c.getString(1), c.getString(2),
                    c.getInt(3), c.getString(4), c.getString(5),
                    c.getInt(6) == 1
                )
            )
        }
        c.close()
        return list
    }

    fun markDonorComing(alertId: Int) {
        val cv = ContentValues()
        cv.put("donor_coming", 1)
        writableDatabase.update("alerts", cv, "id=?", arrayOf(alertId.toString()))
    }

    fun getActiveAlertCount(): Int {
        val c = readableDatabase.rawQuery("SELECT COUNT(*) FROM alerts WHERE donor_coming=0", null)
        val count = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return count
    }
}

data class DonorRow(
    val id: Int,
    val name: String,
    val phone: String,
    val bloodGroup: String,
    val location: String,
    val age: Int,
    val registeredDate: String,
    val available: Boolean
)

data class AlertRow(
    val id: Int,
    val bloodGroup: String,
    val hospital: String,
    val units: Int,
    val contact: String,
    val timestamp: String,
    val donorComing: Boolean
)