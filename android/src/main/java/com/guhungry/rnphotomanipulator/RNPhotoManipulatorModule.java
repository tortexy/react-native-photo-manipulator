
package com.guhungry.rnphotomanipulator;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.text.ReactFontManager;
import com.facebook.react.module.annotations.ReactModule;
import com.guhungry.photomanipulator.BitmapUtils;
import com.guhungry.photomanipulator.MimeUtils;
import com.guhungry.rnphotomanipulator.utils.ImageUtils;
import com.guhungry.rnphotomanipulator.utils.ParamUtils;

import android.media.ExifInterface;
import android.graphics.Matrix;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

@ReactModule(name = RNPhotoManipulatorModule.NAME)
public class RNPhotoManipulatorModule extends ReactContextBaseJavaModule {
    public static final String NAME = "RNPhotoManipulator";
    private final String FILE_PREFIX = "RNPM_";
    private final int DEFAULT_QUALITY = 100;

    public RNPhotoManipulatorModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("JPEG", MimeUtils.JPEG);
        constants.put("PNG", MimeUtils.PNG);
        return constants;
    }

    @ReactMethod
    public void batch(String uri, ReadableArray operations, ReadableMap cropRegion, @Nullable ReadableMap targetSize, int quality, String mimeType, Promise promise) {
        try {
            Bitmap output = ImageUtils.cropBitmapFromUri(getReactApplicationContext(), uri, ParamUtils.rectFromMap(cropRegion), ParamUtils.sizeFromMap(targetSize));

            // Operations
            for (int i = 0, count = operations.size(); i < count; i++) {
                processBatchOperation(output, operations.getMap(i));
            }

            // Save & Optimize
            String file = ImageUtils.saveTempFile(getReactApplicationContext(), output, mimeType, FILE_PREFIX, quality);
            output.recycle();

            promise.resolve(file);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    private void processBatchOperation(Bitmap image, ReadableMap operation) {
        if (operation == null) return;
        String type = operation.getString("operation");
        if ("text".equals(type)) {
            ReadableMap text = operation.getMap("options");
            if (text == null) return;

            printLine(image, text.getString("text"), (float) text.getDouble("textSize"), text.getString("fontName"), ParamUtils.pointfFromMap(text.getMap("position")), ParamUtils.colorFromMap(text.getMap("color")), text.getInt("thickness"));
        } else if ("overlay".equals(type)) {
            String uri = operation.getString("overlay");
            if (uri == null) return;

            Bitmap overlay = ImageUtils.bitmapFromUri(getReactApplicationContext(), operation.getString("overlay"));
            BitmapUtils.overlay(image, overlay, ParamUtils.pointfFromMap(operation.getMap("position")));
        }
    }

    @ReactMethod
    public void crop(String uri, ReadableMap cropRegion, @Nullable ReadableMap targetSize, String mimeType, Promise promise) {
        try {
            Bitmap output = ImageUtils.cropBitmapFromUri(getReactApplicationContext(), uri, ParamUtils.rectFromMap(cropRegion), ParamUtils.sizeFromMap(targetSize));

            String file = ImageUtils.saveTempFile(getReactApplicationContext(), output, mimeType, FILE_PREFIX, DEFAULT_QUALITY);
            output.recycle();

            promise.resolve(file);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    private int getImageRotation(int orientation) {
        int rotationDegrees = 0;
        switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_90:
            rotationDegrees = 90;
            break;
        case ExifInterface.ORIENTATION_ROTATE_180:
            rotationDegrees = 180;
            break;
        case ExifInterface.ORIENTATION_ROTATE_270:
            rotationDegrees = 270;
            break;
        }

        return rotationDegrees;
    }
    
    @ReactMethod
    public void overlayImage(String uri, String icon, ReadableMap position, String mimeType, Promise promise) {
        try {
            
            Uri myUri = Uri.parse(uri);
            ExifInterface exif = new ExifInterface(myUri.getPath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            Bitmap output = ImageUtils.bitmapFromUri(getReactApplicationContext(), uri, ImageUtils.mutableOptions());
            
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270 || orientation == ExifInterface.ORIENTATION_ROTATE_180)
            {
                int width = output.getWidth(), height = output.getHeight();

                Matrix matrix = new Matrix();
                // setup rotation degree
                matrix.postRotate(this.getImageRotation(orientation));
                output = Bitmap.createBitmap(output, 0, 0, width, height, matrix, true);
            }
            
            Bitmap overlay = ImageUtils.bitmapFromUri(getReactApplicationContext(), icon);

            BitmapUtils.overlay(output, overlay, ParamUtils.pointfFromMap(position));
            overlay.recycle();

            String file = ImageUtils.saveTempFile(getReactApplicationContext(), output, mimeType, FILE_PREFIX, DEFAULT_QUALITY);
            output.recycle();

            promise.resolve(file);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    public void printText(String uri, ReadableArray list, String mimeType, Promise promise) {
        try {
            Bitmap output = ImageUtils.bitmapFromUri(getReactApplicationContext(), uri, ImageUtils.mutableOptions());

            for (int i = 0, count = list.size(); i < count; i++) {
                ReadableMap text = list.getMap(i);
                if (text == null) continue;
                printLine(output, text.getString("text"), (float) text.getDouble("textSize"), text.getString("fontName"), ParamUtils.pointfFromMap(text.getMap("position")), ParamUtils.colorFromMap(text.getMap("color")), text.getInt("thickness"));
            }

            String file = ImageUtils.saveTempFile(getReactApplicationContext(), output, mimeType, FILE_PREFIX, DEFAULT_QUALITY);
            output.recycle();

            promise.resolve(file);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    private void printLine(Bitmap image, String text, float scale, String fontName, PointF location, int color, int thickness) {
        Typeface font = getFont(fontName);
        BitmapUtils.printText(image, text, location, color, scale, font, Paint.Align.LEFT, thickness);
    }

    private Typeface getFont(String fontName) {
        if (fontName == null) return Typeface.DEFAULT;
        try {
            return ReactFontManager.getInstance().getTypeface(fontName, Typeface.NORMAL, getReactApplicationContext().getAssets());
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    @ReactMethod
    public void optimize(String uri, int quality, Promise promise) {
        try {
            Bitmap output = ImageUtils.bitmapFromUri(getReactApplicationContext(), uri);

            String file = ImageUtils.saveTempFile(getReactApplicationContext(), output, MimeUtils.JPEG, FILE_PREFIX, quality);
            output.recycle();

            promise.resolve(file);
        } catch (Exception e) {
            promise.reject(e);
        }
    }
}
