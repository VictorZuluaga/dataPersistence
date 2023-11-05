package com.example.myapplication

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import androidx.room.Entity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var databaseAirport: AirportDatabase
    private lateinit var databaseFavorite: FavoriteDataBase
    private lateinit var airportDao: AirportDao
    private lateinit var airportsLayout: LinearLayout
    private lateinit var searchEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        databaseAirport = Room.databaseBuilder(applicationContext, AirportDatabase::class.java, "airport_database.db").build()
        airportDao = databaseAirport.airportDao()
        databaseFavorite = Room.databaseBuilder(applicationContext, FavoriteDataBase::class.java, "favorite_database.db").build()

        val sampleAirports = sampleAirports()
        lifecycleScope.launch {
            airportDao.deleteAllAirports()
            databaseFavorite.favoriteDao().deleteAllFavorites()
            sampleAirports.forEach { airport ->
                airportDao.insertAirport(airport)
            }
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        searchEditText = EditText(this)
        searchEditText.hint = "Campo de texto..."
        layout.addView(searchEditText)

        airportsLayout = LinearLayout(this)
        airportsLayout.orientation = LinearLayout.VERTICAL
        layout.addView(airportsLayout)

        setContentView(layout)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                airportsLayout.removeAllViews()

                if (text.isNotEmpty()) {
                    // Código para mostrar aeropuertos cuando hay texto
                    lifecycleScope.launch {
                        try {
                            val airports = withContext(Dispatchers.IO) {
                                airportDao.getAirportsByAirportAutoComplete(text)
                            }
                            airports.forEach { airport ->
                                val airportView = createAirportView(airport)
                                airportsLayout.addView(airportView)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    // Código para mostrar favoritos cuando el texto está vacío
                    lifecycleScope.launch {
                        try {
                            val favorites = withContext(Dispatchers.IO) {
                                databaseFavorite.favoriteDao().getAllFavorites()
                            }
                            airportsLayout.removeAllViews() // Borra vistas existentes
                            favorites.forEach { favorite ->
                                val airportView = createAirportView(Airport(IATA_code = favorite.destination_code, name = favorite.origin_code, passengers = 0))

                                airportsLayout.addView(airportView)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

            }
        })
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun createAirportView(airport: Airport): View {
        val airportView = TextView(this)
        airportView.text = "${airport.name} - ${airport.IATA_code}"
        airportView.setOnClickListener {
            val dummyFlights = generateDummyFlights(airport)
            val flightsLayout = createFlightsLayout(dummyFlights)
            showFlightsDialog(flightsLayout)
        }
        airportView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.setBackgroundColor(Color.LTGRAY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.setBackgroundColor(Color.TRANSPARENT)
                }
            }
            false
        }
        return airportView
    }
    private fun generateDummyFlights(airport: Airport): List<Flight> {
        val flights = mutableListOf<Flight>()
        for (i in 1..5) {
            flights.add(
                Flight(
                    originAirport = airport.IATA_code,
                    destinationAirport = "DEST$i",
                    flightName = "Vuelo $i"
                )
            )
        }
        return flights
    }
    private fun createFlightsLayout(flights: List<Flight>): LinearLayout {
        val flightsLayout = LinearLayout(this)
        flightsLayout.orientation = LinearLayout.VERTICAL
        flights.forEach { flight ->
            val flightView = TextView(this)
            val flightInfo = "${flight.flightName} (Origen: ${flight.originAirport}, Destino: ${flight.destinationAirport})"
            flightView.text = flightInfo
            flightView.setOnClickListener {
                val text = (it as TextView).text.toString()
                println("Seleccionaste vuelo: $text")

                // Cambia el color de fondo del TextView al hacer clic
                flightView.setBackgroundColor(Color.GREEN)

                // Inserta el vuelo en la base de datos Favorite
                val favorite = Favorite(
                    destination_code = flight.destinationAirport,
                    origin_code = flight.originAirport
                )
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        databaseFavorite.favoriteDao().insertFavorite(favorite)
                    }
                }

            }
            flightsLayout.addView(flightView)
        }
        return flightsLayout
    }
    private fun showFlightsDialog(flightsLayout: LinearLayout) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Vuelos")
            .setView(flightsLayout)
            .setPositiveButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

}

@Entity(tableName = "airport")
data class Airport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val IATA_code: String,
    val name: String,
    val passengers: Int
)
data class Flight(
    val originAirport: String,
    val destinationAirport: String,
    val flightName: String
)
@Dao
interface AirportDao {

    @Query("SELECT * FROM airport WHERE IATA_code = :airportIata")
    fun getAirportsByAirport(airportIata: String): List<Airport>

    @Query("SELECT * FROM airport WHERE (:airportIata IS NULL OR IATA_code LIKE '%' || :airportIata || '%') OR (:airportIata IS NULL OR name LIKE '%' || :airportIata || '%')")
    fun getAirportsByAirportAutoComplete(airportIata: String?): List<Airport>

    @Query("DELETE FROM airport")
    suspend fun deleteAllAirports()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAirport(airport: Airport)

    @Delete
    suspend fun deleteAirport(airport: Airport)

}
@Database(entities = [Airport::class], version = 1, exportSchema = false)
abstract class AirportDatabase : RoomDatabase() {
    abstract fun airportDao(): AirportDao
}
@Database(entities = [Favorite::class], version = 1, exportSchema = false)
abstract class FavoriteDataBase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
@Entity(tableName = "favorite")
data class Favorite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val destination_code: String,
    val origin_code: String
)
@Dao
interface FavoriteDao {
    @Insert
    suspend fun insertFavorite(favorite: Favorite)

    @Query("SELECT * FROM favorite")
    suspend fun getAllFavorites(): List<Favorite>

    @Query("DELETE FROM favorite")
    suspend fun deleteAllFavorites()
}
fun sampleAirports(): List<Airport> {
    return listOf(
        Airport(IATA_code = "JFK", name = "John F. Kennedy International Airport", passengers = 6),
        Airport(IATA_code = "LAX", name = "Los Angeles International Airport", passengers = 8),
        Airport(IATA_code = "ORD", name = "O'Hare International Airport", passengers = 7),
        Airport(
            IATA_code = "DFW",
            name = "Dallas/Fort Worth International Airport",
            passengers = 85
        ),
        Airport(
            IATA_code = "ATL",
            name = "Hartsfield-Jackson Atlanta International Airport",
            passengers = 95
        ),
        Airport(IATA_code = "SFO", name = "San Francisco International Airport", passengers = 50),
        Airport(IATA_code = "DEN", name = "Denver International Airport", passengers = 65),
        Airport(IATA_code = "SEA", name = "Seattle-Tacoma International Airport", passengers = 45),
        Airport(IATA_code = "MIA", name = "Miami International Airport", passengers = 40),
        Airport(IATA_code = "LAS", name = "McCarran International Airport", passengers = 42)
    );
}





