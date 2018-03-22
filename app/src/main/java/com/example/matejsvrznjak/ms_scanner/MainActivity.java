package com.example.matejsvrznjak.ms_scanner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private static final int SECOND_ACTIVITY_REQUEST_CODE = 0;

    TextView displayText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayText = (TextView) findViewById(R.id.result_textview);
        displayText.setMovementMethod(new ScrollingMovementMethod());
    }

    public void onSelectImageClick(View view) {
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivityForResult(intent, SECOND_ACTIVITY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // check that it is the SecondActivity with an OK result
        if (requestCode == SECOND_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {

                String imagePath = data.getStringExtra("imagePath");
                String ocrResult = data.getStringExtra("ocrResult");

                if (imagePath != null && ocrResult != null) {
                    BusinessCardData businessCardData = new BusinessCardData();
                    String resutl = businessCardData.processOCRResult(ocrResult);

                    Bitmap b = loadImageFromStorage(imagePath);
                    ImageView img = findViewById(R.id.quick_start_cropped_image);
                    img.setImageBitmap(b);
                    displayText.setText(resutl);
                }
            }
        }
    }

    private Bitmap loadImageFromStorage(String path) {
        try {
            File f = new File(path, "scannedImage.jpg");
            return BitmapFactory.decodeStream(new FileInputStream(f));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
