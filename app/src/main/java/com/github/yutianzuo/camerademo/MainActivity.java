package com.github.yutianzuo.camerademo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv0 = findViewById(R.id.txt0);
        tv0.setOnClickListener(this);

        TextView tv1 = findViewById(R.id.txt1);
        tv1.setOnClickListener(this);

        TextView tv2 = findViewById(R.id.txt2);
        tv2.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        switch (view.getId()) {
            case R.id.txt0:
                Toast.makeText(this, "coming soon", Toast.LENGTH_LONG).show();
                break;
            case R.id.txt1:
                intent = new Intent(this, CustomCamera2.class);
                startActivity(intent);
                break;
            case R.id.txt2:
                Toast.makeText(this, "coming soon", Toast.LENGTH_LONG).show();
                break;
        }
    }
}
