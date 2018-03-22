package com.example.matejsvrznjak.ms_scanner;

/**
 * Created by matejsvrznjak on 28/02/2018.
 */

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import com.example.matejsvrznjak.ms_scanner.R;
import com.example.matejsvrznjak.ms_scanner.views.HUDCanvasView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class ScannerActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, SurfaceHolder.Callback,
        Camera.PictureCallback {

    public static final String WidgetCameraIntent = "WidgetCameraIntent";
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int CREATE_PERMISSIONS_REQUEST_CAMERA = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE = 3;
    private static final int RESUME_PERMISSIONS_REQUEST_CAMERA = 11;
    private static final String TAG = "ScannerActivity";

    public boolean widgetCameraIntent = false;
    private boolean safeToTakePicture;
    private Button scanDocButton;
    private HandlerThread mImageThread;
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private boolean mFocused;
    private HUDCanvasView mHud;
    private View mWaitSpinner;
    private boolean mBugRotate = false;
    private SharedPreferences mSharedPref;
    private SurfaceView mSurfaceView;
    private boolean mFlashMode = false;

    public HUDCanvasView getHUD() {
        return mHud;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.activity_scanner);

        mHud = (HUDCanvasView) findViewById(R.id.hud);
        mWaitSpinner = findViewById(R.id.wait_spinner);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();

        if (Build.VERSION.SDK_INT >= 17)
            display.getRealSize(size);
        else
            display.getSize(size);

        scanDocButton = (Button) findViewById(R.id.scanDocButton);

        scanDocButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPicture();
                waitSpinnerVisible();
            }
        });

        widgetCameraIntent = getIntent().getBooleanExtra(WidgetCameraIntent, false);
        if (widgetCameraIntent) {
            Intent i = new Intent();
            i.setAction(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(getIntent().getComponent());
            setIntent(i);
        }
    }

    public boolean setFlash(boolean stateFlash) {
        PackageManager pm = getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            Camera.Parameters par = mCamera.getParameters();
            par.setFlashMode(stateFlash ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(par);
            Log.d(TAG, "flash: " + (stateFlash ? "on" : "off"));
            return stateFlash;
        }
        return false;
    }

    private void checkResumePermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    RESUME_PERMISSIONS_REQUEST_CAMERA);
        } else {
            enableCameraView();
        }
    }

    private void checkCreatePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE);
        }
    }

    public void turnCameraOn() {
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);

        mSurfaceHolder = mSurfaceView.getHolder();

        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mSurfaceView.setVisibility(SurfaceView.VISIBLE);
    }

    public void enableCameraView() {
        if (mSurfaceView == null) {
            turnCameraOn();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CREATE_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    turnCameraOn();
                }
                break;
            }

            case RESUME_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    enableCameraView();
                }
                break;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        checkResumePermissions();
        checkCreatePermissions();

        if (mImageThread == null) {
            mImageThread = new HandlerThread("Worker Thread");
            mImageThread.start();
        }
    }

    public void waitSpinnerVisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWaitSpinner.setVisibility(View.VISIBLE);
            }
        });
    }

    public void waitSpinnerInvisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWaitSpinner.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void onDestroy() {
        super.onDestroy();
        // FIXME: check disableView()
    }

    public List<Camera.Size> getResolutionList() {
        return mCamera.getParameters().getSupportedPreviewSizes();
    }

    public Camera.Size getMaxPreviewResolution() {
        int maxWidth = 0;
        Camera.Size curRes = null;

        mCamera.lock();

        for (Camera.Size r : getResolutionList()) {
            if (r.width > maxWidth) {
                Log.d(TAG, "supported preview resolution: " + r.width + "x" + r.height);
                maxWidth = r.width;
                curRes = r;
            }
        }
        return curRes;
    }

    public List<Camera.Size> getPictureResolutionList() {
        return mCamera.getParameters().getSupportedPictureSizes();
    }

    public Camera.Size getMaxPictureResolution(float previewRatio) {
        int maxPixels = 0;
        int ratioMaxPixels = 0;
        Camera.Size currentMaxRes = null;
        Camera.Size ratioCurrentMaxRes = null;
        for (Camera.Size r : getPictureResolutionList()) {
            float pictureRatio = (float) r.width / r.height;
            Log.d(TAG, "supported picture resolution: " + r.width + "x" + r.height + " ratio: " + pictureRatio);
            int resolutionPixels = r.width * r.height;

            if (resolutionPixels > ratioMaxPixels && pictureRatio == previewRatio) {
                ratioMaxPixels = resolutionPixels;
                ratioCurrentMaxRes = r;
            }

            if (resolutionPixels > maxPixels) {
                maxPixels = resolutionPixels;
                currentMaxRes = r;
            }
        }

        boolean matchAspect = mSharedPref.getBoolean("match_aspect", true);

        if (ratioCurrentMaxRes != null && matchAspect) {

            Log.d(TAG, "Max supported picture resolution with preview aspect ratio: "
                    + ratioCurrentMaxRes.width + "x" + ratioCurrentMaxRes.height);
            return ratioCurrentMaxRes;
        }
        return currentMaxRes;
    }

    private int findBestCamera() {
        int cameraId = -1;
        //Search for the back facing camera
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
            cameraId = i;
        }
        return cameraId;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            int cameraId = findBestCamera();
            mCamera = Camera.open(cameraId);
        } catch (RuntimeException e) {
            System.err.println(e);
            return;
        }

        Camera.Parameters param;
        param = mCamera.getParameters();

        Camera.Size pSize = getMaxPreviewResolution();
        param.setPreviewSize(pSize.width, pSize.height);

        float previewRatio = (float) pSize.width / pSize.height;

        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();

        if (Build.VERSION.SDK_INT >= 17)
            display.getRealSize(size);
        else
            display.getSize(size);

        int displayWidth = Math.min(size.y, size.x);
        int displayHeight = Math.max(size.y, size.x);

        float displayRatio = (float) displayHeight / displayWidth;

        int previewHeight = displayHeight;

        if (displayRatio > previewRatio) {
            ViewGroup.LayoutParams surfaceParams = mSurfaceView.getLayoutParams();
            previewHeight = (int) ((float) size.y / displayRatio * previewRatio);
            surfaceParams.height = previewHeight;
            mSurfaceView.setLayoutParams(surfaceParams);
            mHud.getLayoutParams().height = previewHeight;
        }

        int hotAreaWidth = displayWidth / 6;
        int hotAreaHeight = previewHeight / 2 - displayWidth / 5;
        int hotAreaHeight2= previewHeight / 2 + displayWidth / 5;

        ImageView angleNorthWest = (ImageView) findViewById(R.id.nw_angle);
        RelativeLayout.LayoutParams paramsNW = (RelativeLayout.LayoutParams) angleNorthWest.getLayoutParams();
        paramsNW.leftMargin = hotAreaWidth - paramsNW.width;
        paramsNW.topMargin = hotAreaHeight - (paramsNW.height + paramsNW.height / 2);
        angleNorthWest.setLayoutParams(paramsNW);

        ImageView angleNorthEast = (ImageView) findViewById(R.id.ne_angle);
        RelativeLayout.LayoutParams paramsNE = (RelativeLayout.LayoutParams) angleNorthEast.getLayoutParams();
        paramsNE.leftMargin = displayWidth - hotAreaWidth;
        paramsNE.topMargin = hotAreaHeight - (paramsNE.height + paramsNE.height / 2);
        angleNorthEast.setLayoutParams(paramsNE);

        ImageView angleSouthEast = (ImageView) findViewById(R.id.se_angle);
        RelativeLayout.LayoutParams paramsSE = (RelativeLayout.LayoutParams) angleSouthEast.getLayoutParams();
        paramsSE.leftMargin = displayWidth - hotAreaWidth;
        paramsSE.topMargin = hotAreaHeight2 - (paramsSE.height + paramsSE.height / 2);
        angleSouthEast.setLayoutParams(paramsSE);

        ImageView angleSouthWest = (ImageView) findViewById(R.id.sw_angle);
        RelativeLayout.LayoutParams paramsSW = (RelativeLayout.LayoutParams) angleSouthWest.getLayoutParams();
        paramsSW.leftMargin = hotAreaWidth - paramsSW.width;
        paramsSW.topMargin = hotAreaHeight2 - (paramsSW.height + paramsSW.height / 2);
        angleSouthWest.setLayoutParams(paramsSW);

        Camera.Size maxRes = getMaxPictureResolution(previewRatio);
        if (maxRes != null) {
            param.setPictureSize(maxRes.width, maxRes.height);
            Log.d(TAG, "max supported picture resolution: " + maxRes.width + "x" + maxRes.height);
        }

        PackageManager pm = getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            param.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            Log.d(TAG, "Enabling Autofocus");
        } else {
            mFocused = true;
            Log.d(TAG, "Autofocus not available");
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            param.setFlashMode(mFlashMode ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
        }

        mCamera.setParameters(param);

        mBugRotate = mSharedPref.getBoolean("bug_rotate", false);

        if (mBugRotate) {
            mCamera.setDisplayOrientation(270);
        } else {
            mCamera.setDisplayOrientation(90);
        }

        if (Build.VERSION.SDK_INT >= 16) {
            try {
                mCamera.setAutoFocusMoveCallback(new Camera.AutoFocusMoveCallback() {
                    @Override
                    public void onAutoFocusMoving(boolean start, Camera camera) {
                        mFocused = !start;
                        Log.d(TAG, "focusMoving: " + mFocused);
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Failed setting AutoFocusMoveCallback");
            }
        }

        // some devices doesn't call the AutoFocusMoveCallback - fake the focus to true at the start
        mFocused = true;
        safeToTakePicture = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        refreshCamera();
    }

    private void refreshCamera() {
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
        }

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
        } catch (Exception e) {
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    public boolean requestPicture() {
        if (safeToTakePicture) {
            safeToTakePicture = false;
            mCamera.takePicture(null, null, this);
            return true;
        }
        return false;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Bitmap bitmap = rotate(BitmapFactory.decodeByteArray(data, 0, data.length), 90);
        String imagePath = saveImageToInternalStorage(getApplicationContext(), bitmap, "scannedImageDir", "scannedImage.jpg");
        File f = new File(imagePath, "scannedImage.jpg");
        Uri imageUri = Uri.fromFile(f);
        CropImage.activity(imageUri).setScaleType(CropImageView.ScaleType.FIT_CENTER).setAspectRatio(40, 25).start(this);
        safeToTakePicture = true;
    }

    public static String saveImageToInternalStorage(Context context, Bitmap bitmapImage, String directoryPath, String fileName){
        ContextWrapper cw = new ContextWrapper(context);
        File directory = cw.getDir(directoryPath, Context.MODE_PRIVATE);
        File filePath = new File(directory,fileName);

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filePath);
            bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return directory.getAbsolutePath();
    }

    public static Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.postRotate(degree);

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), result.getUri());
                    String resultOCR = processImage(bitmap);
                    String imagePath = saveImageToInternalStorage(getApplicationContext(), bitmap, "scannedImageDir", "scannedImage.jpg");
                    openMainWithImage(resultOCR, imagePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return;
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
            }

            waitSpinnerInvisible();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    public void openMainWithImage(String ocrResult, String imagePath) {
        Intent intent = new Intent();
        intent.putExtra("imagePath", imagePath);
        intent.putExtra("ocrResult", ocrResult);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        return false;
    }

    public String processImage(Bitmap image) {
        String OCRresult = "";

        Context context = getApplicationContext();
        TextRecognizer ocrFrame = new TextRecognizer.Builder(context).build();
        Frame frame = new Frame.Builder().setBitmap(image).build();
        if (ocrFrame.isOperational()) {
            Log.e(TAG, "Textrecognizer is operational");
        }

        SparseArray<TextBlock> textBlocks = ocrFrame.detect(frame);

        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));

            List<? extends Text> textComponents = textBlock.getComponents();

            for (Text t : textComponents) {
                Log.e("recognizedText ", t.getValue());
                OCRresult += t.getValue() + "\n";
            }
        }

        return OCRresult;
    }

//
// CLOUD VISION CODE
//

//    private static final String GOOGLE_API_KEY = "YOUR_API_KEY";
//    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
//    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
//
//    public void processImage(Bitmap image) {
//        if (image != null) {
//            try {
//                // scale the image to save on bandwidth
////                Bitmap bitmap = scaleBitmapDown(image, 1200);
////                callCloudVision(bitmap);
//            } catch (IOException e) {
//                Log.d(TAG, "Image picking failed because " + e.getMessage());
//                Toast.makeText(this, "Image picking failed because" + e.getMessage(), Toast.LENGTH_LONG).show();
//            }
//        } else {
//            Log.d(TAG, "Image picker gave us a null image.");
//            Toast.makeText(this, "Image picker gave us a null image.", Toast.LENGTH_LONG).show();
//        }
//    }

//    private void callCloudVision(final Bitmap bitmap) throws IOException {
//        new PostManualCloudVision(bitmap).execute();
//    }
//
//    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {
//
//        int originalWidth = bitmap.getWidth();
//        int originalHeight = bitmap.getHeight();
//        int resizedWidth = maxDimension;
//        int resizedHeight = maxDimension;
//
//        if (originalHeight > originalWidth) {
//            resizedHeight = maxDimension;
//            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
//        } else if (originalWidth > originalHeight) {
//            resizedWidth = maxDimension;
//            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
//        } else if (originalHeight == originalWidth) {
//            resizedHeight = maxDimension;
//            resizedWidth = maxDimension;
//        }
//        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
//    }

//    class PostManualCloudVision extends AsyncTask<Void, Void, String> {
//        Bitmap bitmap;
//        ConnectivityManager connectivityManager;
//
//        PostManualCloudVision(Bitmap bitmap) {
//            this.bitmap = bitmap;
//        }
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//        }
//
//        @Override
//        protected String doInBackground(Void... params) {
//            try {
//                final String api_key = GOOGLE_API_KEY;
//                final String packageName = getPackageName();
//                final String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);
//
//                // Convert the bitmap to a JPEG
//                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
//                byte[] imageBytes = byteArrayOutputStream.toByteArray();
//
//                // Base64 encode the JPEG
//                final Image base64EncodedImage = new Image();
//                base64EncodedImage.encodeContent(imageBytes);
//                String encodedImage = base64EncodedImage.getContent();
//
//                //Assemble json
//                final String strJson = "{\"requests\": { \"features\": [ { \"type\": \"TEXT_DETECTION\" } ], \"image\": { \"content\": \"" + encodedImage + "\" }}}";
//                JSONObject json = new JSONObject(strJson);
//
//                //Assemble post request
//                final URL url = new URL("https://vision.googleapis.com/v1/images:annotate?key=" + api_key);
//                Request request = new Request(connectivityManager, url);
//                request.setMethod("POST");
//                request.setHeader(ANDROID_PACKAGE_HEADER, packageName);
//                request.setHeader(ANDROID_CERT_HEADER, sig);
//                request.setBody(json);
//
//                request.execute();
//
//                int code = request.code;
//                JSONObject response = request.getResponse();
//
//                Log.d(TAG, "Stop (http code " + code + ")");
//
//                JSONObject error = response.optJSONObject("error");
//                if (error != null) {
//                    Log.d(TAG, error.getString("code") + ": " + error.get("message"));
//                } else {
//                    JSONObject responses = response.getJSONArray("responses").getJSONObject(0);
//                    if (responses != null) {
//                        JSONArray textAnnotations = responses.getJSONArray("textAnnotations");
//                        if (textAnnotations != null) {
//
//                            String description = textAnnotations.getJSONObject(0).get("description").toString();
//
//                            if (description != null) {
//                                return description;
//                            }
//                        }
//                    }
//                }
//
//                return response.toString();
//            } catch (Exception e) {
//                return e.getMessage();
//            }
//        }
//
//        protected void onPostExecute(String result) {
//            waitSpinnerInvisible();
//            String imagePath = saveImageToInternalStorage(getApplicationContext(), bitmap, "scannedImageDir", "scannedImage.jpg");
//            openMainWithImage(result, imagePath);
//        }
//    }
}

