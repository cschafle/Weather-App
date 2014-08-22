package com.example.weatherCCS.app;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.example.weatherCCS.app.Networks.CurrentWeather;
import com.example.weatherCCS.app.Networks.GetLocation;
import com.example.weatherCCS.app.Networks.LocationService;
import com.example.weatherCCS.app.Networks.WeatherData;
import com.example.weatherCCS.app.Networks.WeatherForecast;
import com.example.weatherCCS.app.Networks.WeatherNetwork;

import org.apache.http.HttpException;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class MainActivity extends ActionBarActivity {
    private static final String KEY_CURRENT_WEATHER = "key_current_weather";
    private static final String KEY_WEATHER_FORECASTS = "key_weather_forecasts";
    private TextView locationNameTextView;
    private TextView latituteField;
    private TextView longitudeField;
    public double lat;
    public double lon;
    private String provider;
    public Location location;
    private static final long LOCATION_TIMEOUT_SECONDS = 20;
    private final CompositeSubscription mCompositeSubscription = new CompositeSubscription();
    private TextView text;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setUpWeather();
        text = (TextView) findViewById(R.id.text1);
        updateWeather();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onDestroy() {
        mCompositeSubscription.unsubscribe();
        super.onDestroy();
    }

    public void setUpWeather(){

        GetLocation newLocation = new GetLocation(this);
        location = newLocation.getLocation();

        WeatherNetwork weatherNetwork = new WeatherNetwork();
        Context context = getApplicationContext();
        weatherNetwork.callWeatherAPI(new Callback<WeatherData>() {

            public void success(final WeatherData arg0, Response arg1) {
                Toast.makeText(MainActivity.this, "TEMP MAX: " + arg0.main.temp_max, Toast.LENGTH_LONG).show();
                TextView text = (TextView) findViewById(R.id.text0);
                text.setText(""+ arg0.main.temp_max);
                lat = arg0.coord.lat;
                lon = arg0.coord.lon;
                Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + lat + "\nLong: " + lon, Toast.LENGTH_LONG).show();

            }

            public void failure(RetrofitError arg0) {

                Toast.makeText(MainActivity.this, "Oops :(", Toast.LENGTH_LONG).show();
            }
        });
    }


    public void updateWeather() {
        // Get our current location.
        LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

        final LocationService locationService = new LocationService(locationManager);

        final Observable fetchDataObservable = locationService.getLocation()
                .flatMap(new Func1<Location, Observable<?>>() {
                    @Override
                    public Observable<?> call(final Location location) {
                        final WeatherNetwork weatherService = new WeatherNetwork();

                        return Observable.zip(
                                // Fetch current and 7 day forecasts for the location.
                                weatherService.fetchCurrentWeather(lon, lat),
                                weatherService.fetchWeatherForecasts(lon, lat),
                                // Only handle the fetched results when both sets are available.
                                new Func2<CurrentWeather, List<WeatherForecast>, Object>() {
                                    @Override
                                    public Object call(final CurrentWeather currentWeather, final List<WeatherForecast> weatherForecasts) {
                                        HashMap weatherData = new HashMap();
                                        weatherData.put(KEY_CURRENT_WEATHER, currentWeather);
                                        weatherData.put(KEY_WEATHER_FORECASTS, weatherForecasts);
                                        return weatherData;
                                    }
                                }
                        );
                    }
                });
        mCompositeSubscription.add(fetchDataObservable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<HashMap>() {
                    @Override
                    public void onNext(final HashMap weatherData) {
                        // Update UI with current weather.

                        final CurrentWeather currentWeather = (CurrentWeather) weatherData
                                .get(KEY_CURRENT_WEATHER);
                                text.setText(String.valueOf(Math.round((((currentWeather.getMaximumTemperature())))*9)/5 + 32) + "°");
                        // Update weather forecast list.
                        final List<WeatherForecast> weatherForecasts = (List<WeatherForecast>)
                                weatherData.get(KEY_WEATHER_FORECASTS);



                    }
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(final Throwable error) {

                        if (error instanceof TimeoutException) {
                            Toast.makeText(MainActivity.this, "Timeout D:", Toast.LENGTH_LONG).show();
                        } else if (error instanceof RetrofitError
                                || error instanceof HttpException) {
                            Toast.makeText(MainActivity.this, "Could not fetch Weather :/", Toast.LENGTH_LONG).show();
                        } else {
                            error.printStackTrace();
                            throw new RuntimeException("See inner exception");
                        }
                    }
                })
        );

    }

}


