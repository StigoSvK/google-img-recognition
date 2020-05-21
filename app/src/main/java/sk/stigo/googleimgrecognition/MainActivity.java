package sk.stigo.googleimgrecognition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImageView photoResultImageView;
    private TextView recognitionResult;
    private TextView recognitionResultLabel;
    private String currentPhotoPath;
    private Button btnRecognize;
    private ProgressBar spinner;

    static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_items, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        photoResultImageView = findViewById(R.id.photoResult);
        recognitionResult = findViewById(R.id.recognitionResult);
        recognitionResultLabel = findViewById(R.id.recognitionResultLabel);
        btnRecognize = findViewById(R.id.btnRecognize);
        spinner = findViewById(R.id.loadingSpinner);

        setSupportActionBar(toolbar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deletePhoto();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                setPic();
                if (btnRecognize.getVisibility() != View.VISIBLE) {
                    btnRecognize.setVisibility(View.VISIBLE);
                }
                if (recognitionResult.getVisibility() == View.VISIBLE) {
                    if (recognitionResult.getText() != null) {
                        recognitionResult.setText(null);
                    }
                    recognitionResult.setVisibility(View.GONE);
                    recognitionResultLabel.setVisibility(View.GONE);
                }
            } else {
                resetActivityViews();
            }
        }
    }

    public void onBtnTakePictureClick(View view) {
        deletePhoto();
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (photoFile != null) {

                Uri photoURI = FileProvider.getUriForFile(this,
                        "sk.stigo.googleimgrecognition.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    public void onBtnRecognitionClick(View view) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()) ;
        setLoadingSpinnerVisibility(View.VISIBLE);
        if (recognitionResult.getText() != null) {
            recognitionResult.setText(null);
        }
        if (!preferences.getBoolean("parseWaterGaugeValue", false)) {
            recognitionResult.setVisibility(View.GONE);
            recognitionResultLabel.setVisibility(View.GONE);
        }

        Bitmap bitmap;
        try {
            bitmap = getRotatedImg(BitmapFactory.decodeFile(currentPhotoPath, getBitMapOptions()));
            runTextRecognition(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image =  File.createTempFile(
                "cameraResult",  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void setPic() {
        Bitmap bitmap;
        try {
            bitmap = getRotatedImg(BitmapFactory.decodeFile(currentPhotoPath, getBitMapOptions()));
            photoResultImageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runTextRecognition(Bitmap image) {
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(image);
        FirebaseVisionTextRecognizer detector;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()) ;
        if (preferences.getBoolean("cloudRecognition", false)) {
            detector = FirebaseVision.getInstance().getCloudTextRecognizer();
        } else {
            detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
        }

        detector.processImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
            @Override
            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                parseValueFromWaterGauge(firebaseVisionText.getTextBlocks());
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()) ;
                if (preferences.getBoolean("parseWaterGaugeValue", false)) {
                    recognitionResult.setText(parseValueFromWaterGauge(firebaseVisionText.getTextBlocks()));
                    recognitionResult.setVisibility(View.VISIBLE);
                    recognitionResultLabel.setVisibility(View.VISIBLE);
                } else {
                    createAlertDialog(firebaseVisionText.getText());
                }
                setLoadingSpinnerVisibility(View.GONE);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                setLoadingSpinnerVisibility(View.GONE);
                e.printStackTrace();
            }
        });
    }

    private Bitmap getRotatedImg(Bitmap source) throws IOException {
        ExifInterface ei = new ExifInterface(currentPhotoPath);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        switch(orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(source, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(source, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(source, 270);
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                return source;
        }
    }

    private Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    private void deletePhoto() {
        if (currentPhotoPath != null) {
            File f = new File(currentPhotoPath);
            f.delete();
        }
    }

    private BitmapFactory.Options getBitMapOptions() {
        // Get the dimensions of the View
        int targetW = photoResultImageView.getWidth();
        int targetH = photoResultImageView.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;
        return bmOptions;
    }

    private void resetActivityViews() {
        photoResultImageView.setImageBitmap(null);
        if (btnRecognize.getVisibility() != View.GONE) {
            btnRecognize.setVisibility(View.GONE);
        }
        if (recognitionResult.getVisibility() != View.GONE) {
            if (recognitionResult.getText() != null) {
                recognitionResult.setText(null);
            }
            recognitionResult.setVisibility(View.GONE);
            recognitionResultLabel.setVisibility(View.GONE);
        }
    }

    private String parseValueFromWaterGauge(List<FirebaseVisionText.TextBlock> textBlocks) {
//        String a = textBlocks.stream()
//                .map(FirebaseVisionText.TextBlock::getLines)
//                .flatMap(Collection::stream)
//                .filter(line -> line.getText().replaceAll("\\s","").matches("^\\d{4,5}m?3?$"))
//                .findFirst()
//                .get()
//                .getText()
//                .replaceAll("\\s","").replaceFirst("m?3?$", "");

        FirebaseVisionText.Line parsedValue = textBlocks.stream()
                .map(FirebaseVisionText.TextBlock::getLines)
                .flatMap(Collection::stream)
                .filter(line -> line.getText().replaceAll("\\s","").matches("^\\d{4,5}m?3?$"))
                .findFirst().orElse(null);

        return parsedValue != null ? parsedValue.getText().replaceAll("\\s","").replaceFirst("m?3?$", "") : "unable to recognize";

    }

    private void setLoadingSpinnerVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }

        spinner.setVisibility(visibility);
    }

    private void createAlertDialog(String message) {
        new MaterialAlertDialogBuilder(MainActivity.this)
                .setMessage(message)
                .setTitle(R.string.resultDialogTitle)
                .setNegativeButton(R.string.btnCancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

}
