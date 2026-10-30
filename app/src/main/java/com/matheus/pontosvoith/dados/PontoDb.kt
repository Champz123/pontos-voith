package com.matheus.pontosvoith.dados

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Banco local (SQLite nativo): leve, rápido e 100% offline.
 * Versão 2 (V6): colunas "almoco" e "excluido_em" (lixeira). A migração mantém todos os dados.
 */
class PontoDb(ctx: Context) : SQLiteOpenHelper(ctx, "pontos.db", null, 2) {
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
                criado_em INTEGER NOT NULL,
                almoco INTEGER NOT NULL DEFAULT 0,
                excluido_em INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_batidas_data ON batidas(data_hora)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE batidas ADD COLUMN almoco INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE batidas ADD COLUMN excluido_em INTEGER")
        }
    }
}
