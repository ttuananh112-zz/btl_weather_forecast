package cz.martykan.forecastie.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.tasks.GenericRequestTask;
import cz.martykan.forecastie.tasks.ParseResult;
import cz.martykan.forecastie.tasks.TaskOutput;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.UnitConvertor;

public class SuggestDestinationActivity extends AppCompatActivity {

    SeekBar seekBar;
    TextView tvDate;
    ProgressDialog progressDialog;
    SuggestDestinationActivity activity = this;
    SharedPreferences sp;

    private TextView suggestTemperature;
    private TextView suggestDescription;
    private TextView suggestWind;
    private TextView suggestPressure;
    private TextView suggestHumidity;
    private TextView suggestCloud;
    private TextView suggestRain;
    private TextView suggestUvIndex;
    private TextView suggestIcon;
    private TextView suggestCity;
    private Formatting formatting;

    int seekBarDate = 0;

    String[] destinations = new String[]{
            "Thai Nguyen",// - Thái Nguyên
            "Ha Long",// - Hạ Long
//            "Haiphong",// - Hải Phòng
//            "Ha Noi",// - Hà Nội
            "Vinh",// - Vinh
//            "Turan",// - Đà Nẵng
            "Quang Ngai",// - Quảng Ngãi
            "Quy Nhon",// - Quy Nhơn
            "Nha Trang",// - Nha Trang
            "Da Lat",// - Đà Lạt
//            "Phan Rang-Thap Cham",// - Phan Rang
            "Phan Thiet",// - Phan Thiết
//            "Thanh pho Ho Chi Minh",// - TPHCM
            "Vung Tau",// - Vũng Tàu
//            "My Tho",// - Mỹ Tho
            "Can Tho",// - Cần Thơ
//            "Ca Mau",// - Cà Mau
//            "Duong GJong",// - Phú Quốc
//            "Con Son",// - Côn Đảo
//            "Sa Pa",// - Sa Pa
    };

    static double[] standard_value = new double[]{25.0, 100.0, 50.0, 10.0, 1.0, 0.0};
    static double[] nomalized_standard_value = new double[standard_value.length];

    static ArrayList<double[]> dataset = new ArrayList<>();
    static ArrayList<String[]> additions = new ArrayList<>();

    static boolean isLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggest_destination);

        seekBar = findViewById(R.id.seekBar);
        tvDate = findViewById(R.id.tvDate);
        suggestTemperature = findViewById(R.id.suggest_temp);
        suggestDescription = findViewById(R.id.suggest_description);
        suggestWind = findViewById(R.id.suggest_wind);
        suggestPressure = findViewById(R.id.suggest_pressure);
        suggestHumidity = findViewById(R.id.suggest_humidity);
        suggestCloud = findViewById(R.id.suggest_sunrise);
        suggestRain = findViewById(R.id.suggest_sunset);
        suggestUvIndex = findViewById(R.id.suggest_uvindex);
        suggestCity = findViewById(R.id.suggest_city);
        suggestIcon = findViewById(R.id.suggest_icon);
        Typeface weatherFont = Typeface.createFromAsset(this.getAssets(), "fonts/weather.ttf");
        suggestIcon.setTypeface(weatherFont);

        formatting = new Formatting(SuggestDestinationActivity.this);
        sp = PreferenceManager.getDefaultSharedPreferences(this);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(i == 1)
                    tvDate.setText("1 day to go");
                else if(i > 1)
                    tvDate.setText(i + " days to go");
                else
                    tvDate.setText("Now");

                seekBarDate = i;
                isLoaded = false;
                for(int j=0; j<destinations.length; j++){
                    progressDialog = new ProgressDialog(SuggestDestinationActivity.this);
                    new SuggestDestinationTask(getBaseContext(), activity, progressDialog).execute("suggest", destinations[j]);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                dataset = new ArrayList<>();
                additions = new ArrayList<>();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    public static double[] mean(){
        double[] meanArray = new double[standard_value.length];
        for(int j=0; j<standard_value.length; j++){
            for(int i=0; i<dataset.size(); i++){
                meanArray[j] += dataset.get(i)[j];
            }
            meanArray[j] /= dataset.size();
        }
        return meanArray;
    }

    public static double[] standard_deviation(double[] mean){
//        double[] mean = mean();
        double[][] std_dev = new double[dataset.size()][standard_value.length];
        for(int j=0; j<standard_value.length; j++){
            for(int i=0; i<dataset.size(); i++){
                std_dev[i][j] = Math.pow((dataset.get(i)[j] - mean[j]),2);
            }
        }

        for(int j=0; j<standard_value.length; j++){
            for(int i=1; i<dataset.size(); i++){
                std_dev[0][j] += std_dev[i][j] ;
            }
        }

        for(int i=0; i<standard_value.length; i++){
            std_dev[0][i] /= (dataset.size()-1);
            std_dev[0][i] = Math.sqrt(std_dev[0][i]);
        }

        return std_dev[0];
    }

    public static double[][] normalize(){
        double[] mean = mean();
        double[] std_dev = standard_deviation(mean);
        Log.d("POST EXECUTE", mean[0] + " " + std_dev[0]);
        double[][] norm = new double[dataset.size()][standard_value.length];

        for(int j=0; j<standard_value.length; j++){
            // standard value
            nomalized_standard_value[j] = (standard_value[j] - mean[j]) / std_dev[j];

            for(int i=0; i<dataset.size(); i++){
                norm[i][j] = (dataset.get(i)[j] - mean[j]) / std_dev[j];
            }
        }
        return norm;
    }

    public static int min(double[] loss){
        int minIndex = 0;
        for(int i=1; i<loss.length; i++){
            if(loss[i] < loss[minIndex]){
                minIndex = i;
            }
        }
        return minIndex;
    }

    public static int suggestDestinationIndex(){
        double[][] norm = normalize();
        double[] loss = new double[dataset.size()];
        for(int i=0; i<dataset.size(); i++){
            for(int j=0; j<standard_value.length; j++){
                loss[i] += Math.pow((nomalized_standard_value[j] -  norm[i][j]), 2);
            }
        }
        return min(loss);
    }

    class SuggestDestinationTask extends GenericRequestTask {

        public SuggestDestinationTask(Context context, Activity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected void onPostExecute(TaskOutput output) {
            super.onPostExecute(output);
            if(isLoaded){
                int suggestIndex = suggestDestinationIndex();
                double[] data = dataset.get(suggestIndex);
                Log.d("RESULT SUGGESTION", destinations[suggestIndex]);

                //Set text
                suggestTemperature.setText(String.format("%.0f °C", data[0]));
                suggestDescription.setText(additions.get(suggestIndex)[0]);
                suggestWind.setText(String.format("Wind : %.2f m/s",data[4]));
                suggestPressure.setText(String.format("Pressure : %.2f hpa", data[1]));
                suggestHumidity.setText(String.format("Humidity : %.0f", data[2]) + " %");
                suggestCloud.setText(String.format("Cloud : %.0f", data[3]) + " %");
                suggestRain.setText(String.format("Rain : %.2f", data[5]));
                suggestCity.setText(destinations[suggestIndex]);
                suggestIcon.setText(formatting.setWeatherIcon(Integer.parseInt(additions.get(suggestIndex)[1]), Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));
                Log.d("TEST", additions.get(suggestIndex)[1] + " " + getApplicationContext().getString(R.string.weather_rainy));
            }
        }

        @Override
        protected ParseResult parseResponse(String response) {
            try {
                JSONObject reader = new JSONObject(response);

                final String code = reader.optString("cod");
                if ("404".equals(code)) {
                    return ParseResult.CITY_NOT_FOUND;
                }
                JSONArray list = reader.getJSONArray("list");
                JSONObject item = list.getJSONObject(seekBarDate);
                JSONObject main = item.getJSONObject("main");

                String temp = main.getString("temp");
                String pressure = main.getString("pressure");
                String humidity = main.getString("humidity");
                String cloud = item.getJSONObject("clouds").getString("all");
                String wind = item.getJSONObject("wind").getString("speed");
                JSONObject rainObj = item.optJSONObject("rain");
                String rain = (rainObj != null)?MainActivity.getRainString(rainObj):"0";
                String desc = item.getJSONArray("weather").getJSONObject(0).getString("description");
                String idString = item.getJSONArray("weather").getJSONObject(0).getString("id");


                double convertedTemp = UnitConvertor.convertTemperature(Float.valueOf(temp), sp);
                double convertedPressure = UnitConvertor.convertPressure(Float.valueOf(pressure), sp);
                double convertedHumidity = Float.valueOf(humidity);
                double convertedCloud = Float.valueOf(cloud);
                double convertedWind = Double.valueOf(wind);
                double convertedRain = Float.valueOf(rain);


                Log.d("AsyncTask", convertedTemp +" "+ convertedPressure + " " +
                        convertedHumidity + " " + convertedCloud + " " + convertedWind + " " + convertedRain);

                double[] data = new double[]{convertedTemp, convertedPressure, convertedHumidity,
                        convertedCloud, convertedWind, convertedRain};

                dataset.add(data);
                additions.add(new String[]{desc, idString});

                Log.d("POST EXECUTE", dataset.size() + " " + destinations.length);
                if(dataset.size() == destinations.length){
                    isLoaded = true;
                }


            } catch (JSONException e) {
                Log.e("JSONException Data", response);
                e.printStackTrace();
                return ParseResult.JSON_EXCEPTION;
            }

            return ParseResult.OK;
        }

        @Override
        protected String getAPIName() {
            return "forecast";
        }


    }
}
