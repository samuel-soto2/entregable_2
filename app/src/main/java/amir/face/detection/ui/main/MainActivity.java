package amir.face.detection.ui.main;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.annotation.KeepName;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.hsalf.smilerating.BaseRating;
import com.hsalf.smilerating.SmileRating;

import java.io.IOException;

import amir.face.detection.R;
import amir.face.detection.ui.photo_viewer.PhotoViewerActivity;
import amir.face.detection.utils.base.BaseActivity;
import amir.face.detection.utils.base.Cons;
import amir.face.detection.utils.common.CameraSource;
import amir.face.detection.utils.common.CameraSourcePreview;
import amir.face.detection.utils.interfaces.FaceDetectStatus;
import amir.face.detection.utils.common.FrameMetadata;
import amir.face.detection.utils.interfaces.FrameReturn;
import amir.face.detection.utils.common.GraphicOverlay;
import amir.face.detection.utils.base.PublicMethods;
import amir.face.detection.utils.models.RectModel;
import amir.face.detection.utils.visions.FaceDetectionProcessor;

import static amir.face.detection.utils.base.Cons.IMG_EXTRA_KEY;

@KeepName
public final class MainActivity extends BaseActivity
        implements OnRequestPermissionsResultCallback, FrameReturn, FaceDetectStatus {
    private static final String FACE_DETECTION = "Face Detection";
    private static final String TAG = "MLKitTAG";

    Bitmap originalImage = null;
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private ImageView faceFrame;
    private ImageView test;
    private Button takePhoto;
    private SmileRating smile_rating;
    private Bitmap croppedImage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        test = findViewById(R.id.test);
        preview = findViewById(R.id.firePreview);
        takePhoto = findViewById(R.id.takePhoto);
        faceFrame = findViewById(R.id.faceFrame);
        graphicOverlay = findViewById(R.id.fireFaceOverlay);
        smile_rating = findViewById(R.id.smile_rating);
        FirebaseApp.initializeApp(this);
        if (PublicMethods.allPermissionsGranted(this)) {
            createCameraSource();
        } else {
            PublicMethods.getRuntimePermissions(this);
        }

        takePhoto.setOnClickListener(v -> takePhoto());
    }

    private void createCameraSource() {
        Log.d(TAG, "Creating camera source...");
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        try {
            FaceDetectionProcessor processor = new FaceDetectionProcessor(getResources());
            processor.frameHandler = this;
            processor.faceDetectStatus = this;
            cameraSource.setMachineLearningFrameProcessor(processor);
        } catch (Exception e) {
            Log.e(TAG, "Can not create image processor: " + FACE_DETECTION, e);
            Toast.makeText(
                            getApplicationContext(),
                            "Can not create image processor: " + e.getMessage(),
                            Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void startCameraSource() {
        Log.d(TAG, "Starting camera source...");
        if (cameraSource != null) {
            try {
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        } else {
            Log.d(TAG, "Camera source is null.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming activity, starting camera source.");
        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Pausing activity, stopping camera preview.");
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying activity, releasing camera source.");
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "Permissions result received.");
        if (PublicMethods.allPermissionsGranted(this)) {
            Log.d(TAG, "All permissions granted, creating camera source.");
            createCameraSource();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onFrame(Bitmap image, FirebaseVisionFace face, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay) {
        originalImage = image;
        if (face.getLeftEyeOpenProbability() < 0.4) {
            findViewById(R.id.rightEyeStatus).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.rightEyeStatus).setVisibility(View.INVISIBLE);
        }
        if (face.getRightEyeOpenProbability() < 0.4) {
            findViewById(R.id.leftEyeStatus).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.leftEyeStatus).setVisibility(View.INVISIBLE);
        }

        int smile = 0;

        if (face.getSmilingProbability() > .8) {
            smile = BaseRating.GREAT;
        } else if (face.getSmilingProbability() <= .8 && face.getSmilingProbability() > .6) {
            smile = BaseRating.GOOD;
        } else if (face.getSmilingProbability() <= .6 && face.getSmilingProbability() > .4) {
            smile = BaseRating.OKAY;
        } else if (face.getSmilingProbability() <= .4 && face.getSmilingProbability() > .2) {
            smile = BaseRating.BAD;
        }
        smile_rating.setSelectedSmile(smile, true);

    }

    @Override
    public void onFaceLocated(RectModel rectModel) {
        faceFrame.setColorFilter(ContextCompat.getColor(this, R.color.green));
        takePhoto.setEnabled(true);

        float left = (float) (originalImage.getWidth() * 0.2);
        float newWidth = (float) (originalImage.getWidth() * 0.6);

        float top = (float) (originalImage.getHeight() * 0.2);
        float newHeight = (float) (originalImage.getHeight() * 0.6);
        croppedImage =
                Bitmap.createBitmap(originalImage,
                        ((int) (left)),
                        (int) (top),
                        ((int) (newWidth)),
                        (int) (newHeight));
        test.setImageBitmap(croppedImage);
    }

    private void takePhoto() {
        if (croppedImage != null) {
            String path = PublicMethods.saveToInternalStorage(croppedImage, Cons.IMG_FILE, mActivity);
            startActivity(new Intent(mActivity, PhotoViewerActivity.class)
                    .putExtra(IMG_EXTRA_KEY, path));
        }
    }

    @Override
    public void onFaceNotLocated() {
        faceFrame.setColorFilter(ContextCompat.getColor(this, R.color.red));
        takePhoto.setEnabled(false);
    }
}