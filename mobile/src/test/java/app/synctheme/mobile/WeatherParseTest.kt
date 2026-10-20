package app.synctheme.mobile

import app.synctheme.mobile.data.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherParseTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesForecast() {
        val data = WeatherRepository.parseForecast(res("forecast.json"), "Москва", 0L)
        assertEquals(11.4, data.now.temp, 0.01)
        assertEquals(2, data.now.code)
        assertEquals(true, data.now.isDay)
        assertEquals(6, data.hours.size)
        assertEquals("15:00", data.hours.first().time)
        assertEquals(5, data.days.size)
        assertEquals(-3.5, data.days[4].min, 0.01)
    }

    @Test
    fun parsesCitySearch() {
        val places = WeatherRepository.parseSearch(res("geocoding.json"))
        assertEquals(2, places.size)
        assertEquals("Москва", places[0].name)
        assertEquals("Москва, Россия", places[0].region)
    }
}
