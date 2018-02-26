package com.example.matejsvrznjak.ms_scanner;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {


    static {
        if (!OpenCVLoader.initDebug()) {
            Log.i("opencv", "opencv initialization failed");

        } else {
            Log.i("opencv", "opencv initialization successfull");
        }
    }

    BusinessCardData businessCardData;
    TextView displayText;

    private TessBaseAPI mTess;
    String datapath = "";
    private Mat imageMat;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    imageMat = new Mat();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayText = (TextView) findViewById(R.id.result_textview);

        String language = "eng";
        datapath = getFilesDir()+ "/tesseract/";
        mTess = new TessBaseAPI();
        mTess.setVariable("tessedit_char_whitelist", "+-:.,;()@/0123456789abcčćdđéefghijklmnopqrsštuvwxyzžABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽ");
        checkFile(new File(datapath + "tessdata/"));
        mTess.init(datapath, language);

//        if (!OpenCVLoader.initDebug()) {
//            //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
//        } else {
//            //mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
//        }
    }

    public void onResume()
    {
        super.onResume();
//        if (!OpenCVLoader.initDebug()) {
//            //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
//        } else {
//            //mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
//        }
    }

    /** Start pick image activity with chooser. */
    public void onSelectImageClick(View view) {

//        Rect box = new Rect(5, 10, 20, 20);
//        Rect box= new Rect(5,10,20,30);
//        android.graphics.Rect box = new android.graphics.Rect(50, 100, 150, 200);

        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .setActivityTitle("My Crop")
                .setCropShape(CropImageView.CropShape.RECTANGLE)
                .setCropMenuCropButtonTitle("Done")
//                .setInitialCropWindowRectangle(box)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // handle result of CropImageActivity
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {

                ImageView imageView = findViewById(R.id.quick_start_cropped_image);
                try {
                    imageMat = new Mat();
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), result.getUri());
                    Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Utils.bitmapToMat(bmp32, imageMat);
                    Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY);
                    Imgproc.blur(imageMat, imageMat, new Size(3.0, 3.0));
                    Imgproc.threshold(imageMat, imageMat, 0, 255, Imgproc.THRESH_OTSU);

                    Bitmap bmp = Bitmap.createBitmap(imageMat.cols(), imageMat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(imageMat, bmp);

                    imageView.setImageBitmap(bmp);
                    processImage(bmp);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Toast.makeText(
                        this, "Cropping successful, Sample: " + result.getSampleSize(), Toast.LENGTH_LONG)
                        .show();
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, "Cropping failed: " + result.getError(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void processImage(Bitmap image) {
        businessCardData = new BusinessCardData();
        String resultOutput = "";

        String OCRresult = "";
        mTess.setImage(image);
        OCRresult = mTess.getUTF8Text();
        //displayText.setText(OCRresult);
//        displayText.setText(OCRresult);

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

        resultOutput += "Url:\n";
        for (String url : businessCardData.url) {
            resultOutput += url + "\n";
        }

        resultOutput += "Email:\n";
        for (String email : businessCardData.emails) {
            resultOutput += email + "\n";
        }

        resultOutput += "Address: ";
        for (String address : businessCardData.addresses) {
            resultOutput += address + " ";
        }

        resultOutput += "\n\nOther:\n";
        for (String other : businessCardData.other) {
            resultOutput += other + "\n";
        }

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

    private void checkFile(File dir) {
        //directory does not exist, but we can successfully create it
        if (!dir.exists()&& dir.mkdirs()){
            copyFiles();
        }
        //The directory exists, but there is no data file in it
        if(dir.exists()) {
            String datafilepath = datapath+ "/tessdata/eng.traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles();
            }
        }
    }

    private void copyFiles() {
        try {
            //location we want the file to be at
            String filepath = datapath + "/tessdata/eng.traineddata";

            //get access to AssetManager
            AssetManager assetManager = getAssets();

            //open byte streams for reading/writing
            InputStream instream = assetManager.open("tessdata/eng.traineddata");
            OutputStream outstream = new FileOutputStream(filepath);

            //copy the file to the location specified by filepath
            byte[] buffer = new byte[1024];
            int read;
            while ((read = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, read);
            }
            outstream.flush();
            outstream.close();
            instream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void findRectangle(Mat src) throws Exception {
        Mat blurred = src.clone();
        Imgproc.medianBlur(src, blurred, 9);

        Mat gray0 = new Mat(blurred.size(), CvType.CV_8U), gray = new Mat();

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        List<Mat> blurredChannel = new ArrayList<Mat>();
        blurredChannel.add(blurred);
        List<Mat> gray0Channel = new ArrayList<Mat>();
        gray0Channel.add(gray0);

        MatOfPoint2f approxCurve;

        double maxArea = 0;
        int maxId = -1;

        for (int c = 0; c < 3; c++) {
            int ch[] = { c, 0 };
            Core.mixChannels(blurredChannel, gray0Channel, new MatOfInt(ch));

            int thresholdLevel = 1;
            for (int t = 0; t < thresholdLevel; t++) {
                if (t == 0) {
                    Imgproc.Canny(gray0, gray, 10, 20, 3, true); // true ?
                    Imgproc.dilate(gray, gray, new Mat(), new Point(-1, -1), 1); // 1
                    // ?
                } else {
                    Imgproc.adaptiveThreshold(gray0, gray, thresholdLevel,
                            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                            Imgproc.THRESH_BINARY,
                            (src.width() + src.height()) / 200, t);
                }

                Imgproc.findContours(gray, contours, new Mat(),
                        Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                for (MatOfPoint contour : contours) {
                    MatOfPoint2f temp = new MatOfPoint2f(contour.toArray());

                    double area = Imgproc.contourArea(contour);
                    approxCurve = new MatOfPoint2f();
                    Imgproc.approxPolyDP(temp, approxCurve,
                            Imgproc.arcLength(temp, true) * 0.02, true);

                    if (approxCurve.total() == 4 && area >= maxArea) {
                        double maxCosine = 0;

                        List<Point> curves = approxCurve.toList();
                        for (int j = 2; j < 5; j++) {

                            double cosine = Math.abs(angle(curves.get(j % 4),
                                    curves.get(j - 2), curves.get(j - 1)));
                            maxCosine = Math.max(maxCosine, cosine);
                        }

                        if (maxCosine < 0.3) {
                            maxArea = area;
                            maxId = contours.indexOf(contour);
                        }
                    }
                }
            }
        }

        if (maxId >= 0) {
            Imgproc.drawContours(src, contours, maxId, new Scalar(255, 0, 0,
                    .8), 8);

        }
    }

    private double angle(Point p1, Point p2, Point p0) {
        double dx1 = p1.x - p0.x;
        double dy1 = p1.y - p0.y;
        double dx2 = p2.x - p0.x;
        double dy2 = p2.y - p0.y;
        return (dx1 * dx2 + dy1 * dy2)
                / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2)
                + 1e-10);
    }
}
