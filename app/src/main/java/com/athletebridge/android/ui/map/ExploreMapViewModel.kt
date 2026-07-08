package com.athletebridge.android.ui.map

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class MapPinType { PLACE, COACH }

data class MapPin(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val type: MapPinType
)

data class ExploreMapUiState(
    val pins: List<MapPin> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ExploreMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val placeRepository: PlaceRepository,
    private val coachRepository: CoachRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreMapUiState())
    val uiState: StateFlow<ExploreMapUiState> = _uiState.asStateFlow()

    // Session-scoped geocode cache: address/zip -> lat,lon (null = lookup failed)
    private val geocodeCache = mutableMapOf<String, Pair<Double, Double>?>()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Venue pins from place addresses
            val places = placeRepository.fetchPlaces()
            for (place in places) {
                if (place.address.isBlank()) continue
                geocode(place.address)?.let { (lat, lon) ->
                    addPin(MapPin(place.id, place.name, place.address, lat, lon, MapPinType.PLACE))
                }
            }

            // Coach pins from zip code (fall back to city)
            val coaches = coachRepository.fetchAllCoaches().filter { it.hasValidName }
            for (coach in coaches) {
                val query = coach.zipCode?.takeIf { it.isNotBlank() }
                    ?: coach.city?.takeIf { it.isNotBlank() }
                    ?: continue
                geocode(query)?.let { (lat, lon) ->
                    val subtitle = listOfNotNull(
                        coach.city,
                        coach.minimumRate?.let { "$${it.toInt()}/hr" }
                    ).joinToString(" · ")
                    addPin(MapPin(coach.id, coach.name, subtitle, lat, lon, MapPinType.COACH))
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private fun addPin(pin: MapPin) {
        // Spread pins sharing the exact same geocoded point (e.g. same zip) so all stay tappable
        val jitter = _uiState.value.pins.count {
            it.latitude == pin.latitude && it.longitude == pin.longitude
        } * 0.0015
        val adjusted = if (jitter > 0) pin.copy(latitude = pin.latitude + jitter) else pin
        _uiState.value = _uiState.value.copy(pins = _uiState.value.pins + adjusted)
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(query: String): Pair<Double, Double>? {
        geocodeCache[query]?.let { return it }
        if (geocodeCache.containsKey(query)) return null
        val result = withContext(Dispatchers.IO) {
            try {
                Geocoder(context).getFromLocationName(query, 1)
                    ?.firstOrNull()
                    ?.let { it.latitude to it.longitude }
            } catch (_: Exception) {
                null
            }
        }
        geocodeCache[query] = result
        return result
    }
}
