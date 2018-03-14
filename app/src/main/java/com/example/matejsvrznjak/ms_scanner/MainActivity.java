package com.example.matejsvrznjak.ms_scanner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final int SECOND_ACTIVITY_REQUEST_CODE = 0;

    BusinessCardData businessCardData;
    TextView displayText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayText = (TextView) findViewById(R.id.result_textview);
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
                    loadImageFromStorage(ocrResult, imagePath);
                }
            }
        }
    }

    public void processImage(String ocrResult) {
        businessCardData = new BusinessCardData();
        String resultOutput = "";

        String OCRresult = ocrResult;

        OCRresult = OCRresult.replace("(0)", "").replace("(O)", "").replace("[0)", "").replace("(0]", "").replace("[0]", "").replace("[O]", "");
        OCRresult = OCRresult.replaceAll("[^+:.,()@/0123456789abcčćdđéefghijklmnopqrsštuvwxyzžABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽ\\n]+", " ");

        boolean nameDone = false;

        ArrayList<String> stringList = new ArrayList<>(Arrays.asList(OCRresult.split("\\r?\\n"))); //new ArrayList is only needed if you absolutely need an ArrayList
        ArrayList<String> stringList2 = separate(stringList, "/");
//        ArrayList<String> stringList3 = separate(stringList2, ",");
        ArrayList<String> allLines = separate(stringList2, ";");

        for (String line : allLines) {
            line = line.trim();

            if (line.toLowerCase().contains("m:") || line.toLowerCase().contains("mob") || line.toLowerCase().contains("gsm")) {
                PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                Iterable<PhoneNumberMatch> numbers = phoneUtil.findNumbers(line, "EN");

                for (PhoneNumberMatch num : numbers) {
                    businessCardData.mobile = num.rawString();
                    break;
                }

               // allLines.remove(line);
            } else if (line.toLowerCase().contains("t:") || line.toLowerCase().contains("tel") || line.toLowerCase().contains("stac")) {

                PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                Iterable<PhoneNumberMatch> numbers = phoneUtil.findNumbers(line, "EN");

                for (PhoneNumberMatch num : numbers) {
                    businessCardData.telephone = num.rawString();
                    break;
                }

//                allLines.remove(line);
            } else if (line.toLowerCase().contains("d.o.o.") || line.toLowerCase().contains("s.p.") || line.toLowerCase().contains("co.") || line.toLowerCase().contains("d.d.")) {
                businessCardData.company = line;
//                allLines.remove(line);
            } else {
                Matcher emailMatcher = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(line);
                Matcher nameMatcher = Pattern.compile("([A-ZĆČŠŽ]{1}[A-ZĆČŠŽa-zéćčšž]{3,}[ ]+[A-ZĆČŠŽ]{1}[A-ZĆČŠŽa-zéćčšž]{3,})[ ]*?").matcher(line);
                Matcher webMatcher = Pattern.compile("(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]\\.[^\\s]{2,})").matcher(line);
                Matcher addressMatcher = Pattern.compile("([a-zA-Z]{3,}+\\s)[0-9]{1,}").matcher(line); //([a-zA-Z]{3,}+\s)[0-9]+|([0-9]{1,}+\s([a-zA-Z]{2,}))

                if (emailMatcher.find()) {
                    businessCardData.emails.add(emailMatcher.group());
                } else if (webMatcher.find()) {
                    businessCardData.url.add(webMatcher.group());
                } else if (nameMatcher.find()) {
                    if (!nameDone) {
                        businessCardData.name = nameMatcher.group();
                        nameDone = true;
                    } else if (addressMatcher.find()) {
                        businessCardData.addresses.add(line);
                    } else {
                        businessCardData.other.add(line);
                    }
                } else if (addressMatcher.find()) {
                    businessCardData.addresses.add(line);
                } else {
                    businessCardData.other.add(line);
                }
            }
        }

        resultOutput += "Name: " + businessCardData.name + "\n";
        resultOutput += "Mobile: " + businessCardData.mobile + "\n";
        resultOutput += "Telephone: " + businessCardData.telephone + "\n";
        resultOutput += "Company: " + businessCardData.company + "\n";

        for (String url : businessCardData.url) {
            resultOutput += "Url: ";
            resultOutput += url + "\n";
        }

        resultOutput += "Email: ";
        for (String email : businessCardData.emails) {
            resultOutput += email + "\n";
        }

        resultOutput += "Address: ";
        for (String address : businessCardData.addresses) {
            resultOutput += address + " ";
        }

//        resultOutput += "\n\nOther:\n";
//        for (String other : businessCardData.other) {
//            resultOutput += other + "\n";
//        }

        displayText.setText(resultOutput);
    }

    private ArrayList<String> separate(ArrayList<String> currentArray, String separator) {
        ArrayList<String> stringList = new ArrayList<>();

        for (String item : currentArray) {
            String[] splitItems = item.split(separator);
            if (splitItems.length > 1) {
                for (String newItem : splitItems) {
                    stringList.add(newItem);
                }
            } else {
                stringList.add(item);
            }
        }

        return  stringList;
    }

    private void loadImageFromStorage(String ocrResult, String path) {

        try {
            File f = new File(path, "scannedImage.jpg");
            Bitmap b = BitmapFactory.decodeStream(new FileInputStream(f));

            ImageView img = findViewById(R.id.quick_start_cropped_image);
            img.setImageBitmap(b);

            processImage(ocrResult);
            f.delete();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
