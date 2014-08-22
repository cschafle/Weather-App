package com.example.weatherCCS.app.Networks;

import android.content.Context;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;
import android.location.Location;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import org.apache.http.HttpException;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import retrofit.http.Query;
import retrofit.mime.TypedFile;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.mime.TypedFile;


/**
 * Created by corinneschafle on 7/22/14.
 */
public class WeatherNetwork {
    private static String TAG = "WeatherNetwork";
    private static WeatherNetwork instance = null;
    // This is the base URL for the API
    private static String mBaseUrl =  "api.openweathermap.org";


    private static final RequestInterceptor requestInterceptor = new RequestInterceptor() {
        @Override
        public void intercept(RequestInterceptor.RequestFacade request) {
            request.addHeader("Accept", "application/json");
        }
    };

    private static final RestAdapter restAdapter = new RestAdapter.Builder()
            .setEndpoint("http://api.openweathermap.org")
            .setRequestInterceptor(requestInterceptor)
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .build();

    private static final WeatherService apiManager = restAdapter.create(WeatherService.class);

    public interface WeatherService {
        @GET("/data/2.5/forecast")  //data/2.5/forecast?lat=35&lon=139
        void getForcastforLatLon(@Query("lat") int lat, @Query("lon") int lon);

        @GET("/data/2.5/forecast")  //data/2.5/forecast?q=London,us
        void getForcastForCity(@Query("q") String location);

        @GET("/data/2.5/weather")  //data/2.5/forecast?q=London,us
        void getWeatherForCity(@Query("q") String location, Callback<WeatherData> callback);

        @GET("/data/2.5/weather")  //data/2.5/forecast?q=London,us
        WeatherData getWeather(@Query("q") String location);

        @GET("/data/2.5/weather?units=metric")
        Observable<CurrentWeatherDataEnvelope> fetchCurrentWeather(@Query("lon") double longitude, @Query("lat") double latitude);

        @GET("/data/2.5/forecast/daily?units=metric&cnt=7")
        Observable<WeatherForecastListDataEnvelope> fetchWeatherForecasts(@Query("lon") double longitude, @Query("lat") double latitude);

    }


    public void callWeatherAPI(Callback<WeatherData> weatherData){
        apiManager.getWeatherForCity("Portland, us", weatherData);
    }

    public Observable<CurrentWeather> fetchCurrentWeather(final double longitude,
                                                          final double latitude) {
        return apiManager.fetchCurrentWeather(longitude, latitude)
                .flatMap(new Func1<CurrentWeatherDataEnvelope,
                        Observable<? extends CurrentWeatherDataEnvelope>>() {

                    // Error out if the request was not successful.
                    @Override
                    public Observable<? extends CurrentWeatherDataEnvelope> call(
                            final CurrentWeatherDataEnvelope data) {
                        return data.filterWebServiceErrors();
                    }

                }).map(new Func1<CurrentWeatherDataEnvelope, CurrentWeather>() {

                    // Parse the result and build a CurrentWeather object.
                    @Override
                    public CurrentWeather call(final CurrentWeatherDataEnvelope data) {
                        return new CurrentWeather(data.locationName, data.timestamp,
                                data.weather.get(0).description, data.main.temp,
                                data.main.temp_min, data.main.temp_max);
                    }
                });
    }

    public Observable<List<WeatherForecast>> fetchWeatherForecasts(final double longitude, final double latitude) {
        return apiManager.fetchWeatherForecasts(longitude, latitude)
                .flatMap(new Func1<WeatherForecastListDataEnvelope,
                                        Observable<? extends WeatherForecastListDataEnvelope>>() {
                    @Override
                    public Observable<? extends WeatherForecastListDataEnvelope> call(
                            final WeatherForecastListDataEnvelope listData) {
                        return listData.filterWebServiceErrors();
                    }

                }).map(new Func1<WeatherForecastListDataEnvelope, List<WeatherForecast>>() {
                    @Override
                    public List<WeatherForecast> call(final WeatherForecastListDataEnvelope listData) {
                        final ArrayList<WeatherForecast> weatherForecasts =
                                new ArrayList<WeatherForecast>();

                        for (WeatherForecastListDataEnvelope.ForecastDataEnvelope data : listData.list) {
                            final WeatherForecast weatherForecast = new WeatherForecast(
                                    listData.city.name, data.timestamp, data.weather.get(0).description,
                                    data.temp.min, data.temp.max);
                            weatherForecasts.add(weatherForecast);
                        }

                        return weatherForecasts;
                    }
                });
    }

    /**
     * Base class for results returned by the weather web service.
     */
    private class WeatherDataEnvelope {
        @SerializedName("cod") private int httpCode;

        class Weather {
            public String description;
        }

        /**
         * The web service always returns a HTTP header code of 200 and communicates errors
         * through a 'cod' field in the JSON payload of the response body.
         */
        public Observable filterWebServiceErrors() {
            if (httpCode == 200) {
                return Observable.from(this);
            } else {
                return Observable.error(
                        new HttpException("There was a problem fetching the weather data."));
            }
        }
    }

    /**
     * Data structure for current weather results returned by the web service.
     */
    private class CurrentWeatherDataEnvelope extends WeatherDataEnvelope {
        @SerializedName("name") public String locationName;
        @SerializedName("dt") public long timestamp;
        public ArrayList<Weather> weather;
        public Main main;

        class Main {
            public float temp;
            public float temp_min;
            public float temp_max;
        }
    }

    /**
     * Data structure for weather forecast results returned by the web service.
     */
    private class WeatherForecastListDataEnvelope extends WeatherDataEnvelope {
        public Location city;
        public ArrayList<ForecastDataEnvelope> list;

        class Location {
            public String name;
        }

        class ForecastDataEnvelope {
            @SerializedName("dt") public long timestamp;
            public Temperature temp;
            public ArrayList<Weather> weather;
        }

        class Temperature {
            public float min;
            public float max;
        }
    }

}


