package com.matheus.pontosvoith.dados

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Banco local (SQLite nativo): leve, rápido e 100% offline. */
class PontoDb(ctx: Context) : SQLiteOpenHelper(ctx, "pontos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE batidas(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL,
                data_hora INTEGER NOT NULL,
                latitude REAL,
                longitude REAL,
                precisao REAL,
                endereco TEXT,
                status_local TEXT NOT NULL DEFAULT 'PENDENTE',
                origem TEXT NOT NULL DEFAULT 'NORMAL',
                ajustado INTEGER NOT NULL DEFAULT 0,
                data_ajuste INTEGER,
                valor_original INTEGER,
                obs_ajuste TEXT,
                criado_em INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_batidas_data ON batidas(data_hora)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrações futuras entram aqui (sem apagar dados).
    }
}
