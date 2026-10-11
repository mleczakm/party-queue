package pl.mleczki.partyqueue

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager

/** Works out, in words the host can act on, why a song is not loading. */
class NetworkDiagnosis(private val context: Context) {

    fun reason(loadError: String? = null): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps == null ->
                "Brak połączenia z internetem. Sprawdź Wi-Fi albo dane komórkowe."
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
                "Sieć, z którą połączony jest telefon, nie ma dostępu do internetu."
            cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ->
                "Oszczędzanie danych blokuje transfer w tle. Wyłącz je dla Party Queue (Ustawienia → Połączenia → Wykorzystanie danych)."
            pm.isDeviceIdleMode && !pm.isIgnoringBatteryOptimizations(context.packageName) ->
                "Android wstrzymał sieć w trybie uśpienia. Pozwól aplikacji działać w tle (przycisk u góry ekranu)."
            loadError != null ->
                "Nie udało się wczytać YouTube ($loadError). Sprawdź, czy telefon ma dostęp do youtube.com."
            pm.isPowerSaveMode ->
                "Tryb oszczędzania baterii może spowalniać ładowanie. Poczekaj chwilę albo go wyłącz."
            else ->
                "Ładowanie trwa dłużej niż zwykle. YouTube czasem opóźnia start filmu; poczekaj chwilę."
        }
    }
}
