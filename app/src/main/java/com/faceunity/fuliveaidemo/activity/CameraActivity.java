package com.faceunity.fuliveaidemo.activity;

import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.faceunity.fuliveaidemo.R;
import com.faceunity.fuliveaidemo.renderer.BaseCameraRenderer;
import com.faceunity.fuliveaidemo.renderer.Camera1Renderer;
import com.faceunity.fuliveaidemo.renderer.OnCameraRendererListener;
import com.faceunity.fuliveaidemo.util.LifeCycleSensorManager;
import com.faceunity.fuliveaidemo.util.ToastUtil;
import com.faceunity.fuliveaidemo.util.VideoRecorder;
import com.faceunity.fuliveaidemo.view.RecordButton;
import com.faceunity.nama.FURenderer;

import java.io.File;

/**
 * @author Richie on 2020.05.21
 */
public class CameraActivity extends BaseGlActivity implements OnCameraRendererListener, RecordButton.OnRecordListener,
        FURenderer.OnDebugListener, LifeCycleSensorManager.OnAccelerometerChangedListener, VideoRecorder.OnVideoRecordListener {
    private BaseCameraRenderer mCameraRenderer;
    private RecordButton mRecordBtn;
    private TextView mTvDebug;
    private VideoRecorder mVideoRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LifeCycleSensorManager lifeCycleSensorManager = new LifeCycleSensorManager(this, getLifecycle());
        lifeCycleSensorManager.setOnAccelerometerChangedListener(this);

        mVideoRecorder = new VideoRecorder(mGlSurfaceView);
        mVideoRecorder.setOnVideoRecordListener(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mCameraRenderer.onTouchEvent((int) event.getX(), (int) event.getY(), event.getAction());
        return super.onTouchEvent(event);
    }

    @Override
    protected void initView() {
        findViewById(R.id.iv_save_photo).setVisibility(View.GONE);
        mRecordBtn = findViewById(R.id.btn_record_video);
        mRecordBtn.setOnRecordListener(this);
        mTvDebug = findViewById(R.id.tv_debug);
        mTvDebug.setTag(true);
        findViewById(R.id.iv_debug).setOnClickListener(mViewClickListener);
        findViewById(R.id.iv_switch_cam).setOnClickListener(mViewClickListener);
    }

    @Override
    protected void initFuRenderer() {
        mFURenderer = new FURenderer.Builder(this)
                .setCameraType(Camera.CameraInfo.CAMERA_FACING_BACK)
                .setInputTextureType(FURenderer.INPUT_EXTERNAL_OES_TEXTURE)
                .setInputImageOrientation(FURenderer.getCameraOrientation(Camera.CameraInfo.CAMERA_FACING_BACK))
                .setRunBenchmark(true)
                .setOnDebugListener(this)
                .setOnTrackStatusChangedListener(this)
                .build();
    }

    @Override
    protected void initGlRenderer() {
        mCameraRenderer = new Camera1Renderer(getLifecycle(), this, mGlSurfaceView, this);
    }

    @Override
    protected View getRecordView() {
        return mRecordBtn;
    }

    @Override
    public int onDrawFrame(byte[] cameraNv21Byte, int cameraTexId, int cameraWidth, int cameraHeight, float[] mvpMatrix, float[] texMatrix, long timeStamp) {
        int fuTexId = mFURenderer.drawFrame(cameraNv21Byte, cameraTexId, cameraWidth, cameraHeight);
        trackHuman();
        mPhotoTaker.send(fuTexId, mvpMatrix, texMatrix, mCameraRenderer.getViewWidth(), mCameraRenderer.getViewHeight());
        mVideoRecorder.send(fuTexId, mvpMatrix, texMatrix, timeStamp);
        return fuTexId;
    }

    @Override
    protected void onRenderModeChanged(int renderMode) {
        super.onRenderModeChanged(renderMode);
        final boolean renderController = renderMode == FURenderer.RENDER_MODE_CONTROLLER;
        mCameraRenderer.setRenderRotatedImage(renderController);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecordBtn.setVisibility(renderController ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onCameraChanged(int cameraFacing, int cameraOrientation) {
        mFURenderer.onCameraChanged(cameraFacing, cameraOrientation);
    }

    @Override
    public void onCameraOpened(int cameraWidth, int cameraHeight) {
    }

    @Override
    public void onCameraError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtil.makeText(CameraActivity.this, message);
            }
        });
    }

    @Override
    public void onFpsChanged(final double fps, final double renderTime, double elapsedTime) {
        boolean isShow = (boolean) mTvDebug.getTag();
        if (!isShow) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String text = String.format(getString(R.string.debug_info),
                            mCameraRenderer.getCameraWidth(), mCameraRenderer.getCameraHeight(),
                            (int) fps, (int) renderTime);
                    mTvDebug.setText(text);
                }
            });
        }
    }

    @Override
    public void takePicture() {
        mPhotoTaker.mark();
    }

    @Override
    public void startRecord() {
        int videoWidth = BaseCameraRenderer.DEFAULT_PREVIEW_HEIGHT;
        int videoHeight = mCameraRenderer.getHeight4Video();
        mVideoRecorder.start(videoWidth, videoHeight);
    }

    @Override
    public void stopRecord() {
        mVideoRecorder.stop();
    }

    @Override
    public void onAccelerometerChanged(float x, float y, float z) {
        if (Math.abs(x) > 3 || Math.abs(y) > 3) {
            if (Math.abs(x) > Math.abs(y)) {
                mFURenderer.onDeviceOrientationChanged(x > 0 ? 0 : 180);
            } else {
                mFURenderer.onDeviceOrientationChanged(y > 0 ? 90 : 270);
            }
        }
    }

    @Override
    public void onPrepare() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecordBtn.setSecond(0);
            }
        });
    }

    @Override
    public void onStop(final boolean valid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecordBtn.setSecond(0);
                if (!valid) {
                    ToastUtil.makeText(CameraActivity.this, R.string.save_video_too_short).show();
                }
            }
        });
    }

    @Override
    public void onProgress(final long progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecordBtn.setSecond(progress);
            }
        });
    }

    @Override
    public void onFinish(final String path) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(path))));
                ToastUtil.makeText(CameraActivity.this, R.string.save_video_success).show();
            }
        });
    }

    @Override
    protected void onViewClicked(int id) {
        super.onViewClicked(id);
        switch (id) {
            case R.id.iv_switch_cam: {
                mCameraRenderer.switchCamera();
            }
            break;
            case R.id.iv_debug: {
                boolean isShow = (boolean) mTvDebug.getTag();
                mTvDebug.setTag(!isShow);
                mTvDebug.setVisibility(isShow ? View.VISIBLE : View.GONE);
            }
            break;
            default:
        }
    }

}
