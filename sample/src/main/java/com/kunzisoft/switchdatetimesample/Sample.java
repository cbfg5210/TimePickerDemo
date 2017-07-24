package com.kunzisoft.switchdatetimesample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.util.Calendar;

/**
 * Sample class for an example of using the API SwitchDateTimePicker
 *
 * @author JJamet
 */
public class Sample extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

        Calendar now = Calendar.getInstance();
        NNTimePickerDialog timePickerFragment = NNTimePickerDialog.newInstance(
                new NNTimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(NNTimePickerDialog view, int hourOfDay, int minute, int second) {
                        Toast.makeText(Sample.this, hourOfDay + ":" + minute + ":" + second, Toast.LENGTH_SHORT).show();
                    }
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE)
        );
        getSupportFragmentManager().beginTransaction()
                .add(R.id.rl_root_panel, timePickerFragment)
                .commit();
    }
}