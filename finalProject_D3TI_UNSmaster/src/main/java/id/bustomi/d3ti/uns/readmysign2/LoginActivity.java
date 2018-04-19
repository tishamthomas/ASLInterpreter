package id.bustomi.d3ti.uns.readmysign2;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import id.bustomi.d3ti_uns.readmysign2.R;

/**
 * Created by user on 12-03-2018.
 */

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }
   public void LogInTrain(View view)
   {
       Intent i=new Intent(getApplicationContext(),MainActivity.class);
       i.putExtra("type","Train");
       startActivity(i);
   }
    public void LogInDetect(View view)
    {
        Intent i=new Intent(getApplicationContext(),MainActivity.class);
        i.putExtra("type","Detect");
        startActivity(i);
    }

}
