package com.jonagmz.cuartofrio.datos

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Una lectura: un reporte (instantáneo) o una muestra del historial del equipo (promedio de un intervalo).
 * "clave" evita duplicados cuando el historial se descarga más de una vez.
 */
@Entity(tableName = "lecturas", indices = [Index(value = ["clave"], unique = true), Index("tiempo")])
data class Lectura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clave: String,
    val tiempo: Long,
    val temperatura: Float?,
    val humedad: Float?,
    val ladoCaliente: Float?,
    val potencia: Int?,
    val alarmas: Int,
    val origen: Int,
) {
    companion object {
        const val REPORTE = 0
        const val HISTORIAL = 1
    }
}

@Entity(tableName = "eventos", indices = [Index("tiempo")])
data class Evento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tiempo: Long,
    val tipo: String,
    val titulo: String,
    val detalle: String = "",
) {
    companion object {
        const val ALARMA = "alarma"
        const val ALARMA_RESUELTA = "resuelta"
        const val INICIO = "inicio"
        const val CONFIGURACION = "config"
        const val ERROR = "error"
        const val COMANDO = "comando"
    }
}

@Dao
interface LecturasDao {
    @Query("SELECT * FROM lecturas WHERE tiempo >= :desde ORDER BY tiempo")
    fun desde(desde: Long): Flow<List<Lectura>>

    @Query("SELECT * FROM lecturas ORDER BY tiempo")
    suspend fun todas(): List<Lectura>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(lecturas: List<Lectura>)

    @Query("DELETE FROM lecturas")
    suspend fun borrar()
}

@Dao
interface EventosDao {
    @Query("SELECT * FROM eventos ORDER BY tiempo DESC LIMIT 500")
    fun recientes(): Flow<List<Evento>>

    @Insert
    suspend fun insertar(evento: Evento)

    @Query("DELETE FROM eventos")
    suspend fun borrar()
}

@Database(entities = [Lectura::class, Evento::class], version = 1, exportSchema = true)
abstract class BaseDatos : RoomDatabase() {
    abstract fun lecturas(): LecturasDao
    abstract fun eventos(): EventosDao

    companion object {
        fun crear(context: Context) =
            Room.databaseBuilder(context, BaseDatos::class.java, "cuarto_frio.db").build()
    }
}
