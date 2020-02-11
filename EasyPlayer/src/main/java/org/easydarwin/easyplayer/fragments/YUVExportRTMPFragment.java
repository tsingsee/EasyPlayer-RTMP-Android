package org.easydarwin.easyplayer.fragments;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.util.FileUtil;
import org.easydarwin.easyplayer.util.SPUtil;
import org.easydarwin.easyplayer.views.OverlayCanvasView;
import org.easydarwin.video.EasyRTMPPlayerClient;
import org.easydarwin.video.RTMPClient;

import java.nio.ByteBuffer;

/**
 * rtmp播放器Fragment
 */
public class YUVExportRTMPFragment extends PlayRTMPFragment implements EasyRTMPPlayerClient.I420DataCallback {

    OverlayCanvasView canvas;

    public static YUVExportRTMPFragment newInstance(String url, int type, ResultReceiver rr) {
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, url);
        args.putInt(ARG_PARAM2, type);
        args.putParcelable(ARG_PARAM3, rr);

        YUVExportRTMPFragment fragment = new YUVExportRTMPFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_yuv_rtmp, container, false);
        cover = (ImageView) view.findViewById(R.id.surface_cover);
        canvas = view.findViewById(R.id.overlay_canvas);

        return view;
    }

    @Override
    protected void startRending(SurfaceTexture surface) {
        mStreamRender = new EasyRTMPPlayerClient(getContext(), KEY, new Surface(surface), mResultReceiver, this);

        boolean autoRecord = SPUtil.getAutoRecord(getContext());

        try {
            mStreamRender.start(mUrl,
                    mType,
                    RTMPClient.EASY_SDK_VIDEO_FRAME_FLAG | RTMPClient.EASY_SDK_AUDIO_FRAME_FLAG,
                    "",
                    "",
                    autoRecord ? FileUtil.getMovieName(mUrl).getPath() : null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        sendResult(RESULT_REND_START, null);
    }

    @Override
    public void onMatrixChanged(Matrix matrix, RectF rect) {
        super.onMatrixChanged(matrix, rect);

        if (canvas != null) {
            canvas.setTransMatrix(matrix);
        }
    }

    public void toggleDraw() {
        canvas.toggleDrawable();
    }

    /**
     * 这个buffer对象在回调结束之后会变无效.所以不可以把它保存下来用.如果需要保存,必须要创建新buffer,并拷贝数据.
     * @param buffer
     */
    @Override
    public void onI420Data(ByteBuffer buffer) {
        Log.i(TAG, "I420 data length :" + buffer.capacity());

        // save to local...
//        writeToFile(Environment.getExternalStorageDirectory() +"/EasyPlayer/temp.yuv", buffer);

//        if (i == 200) {
//            try {
//                final byte[] bytes = buffer2Array(buffer);
////            saveYUVtoPicture(bytes, mWidth, mHeight);
////                saveBitmap(yuvToBitmap(bytes, mWidth, mHeight), mWidth, mHeight);
//                saveBitmap(saveYUV2Bitmap(bytes, mWidth, mHeight), mWidth, mHeight);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//
//        i++;
    }

    @Override
    public void onPcmData(byte[] pcm) {
        Log.i(TAG, "pcm data length :" + pcm.length);

//        save2path(pcm, 0, pcm.length,path + "/" + "audio.pcm", true);
    }

//    private String path = Environment.getExternalStorageDirectory() +"/EasyPlayer";
//
//    private static void save2path(byte[] buffer, int offset, int length, String path, boolean append) {
//        FileOutputStream fos = null;
//        try {
//            fos = new FileOutputStream(path, append);
//            fos.write(buffer, offset, length);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if (fos != null) {
//                try {
//                    fos.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
//
//    private int i = 0;
//
////    @Override
////    public void onI420Data(ByteBuffer buffer) {
////        Log.i(TAG, "I420 data length :" + buffer.capacity());
////        // save to local...
////        // writeToFile("/sdcard/tmp.yuv", buffer);
////
//////        if (i++ % 50 != 0)
//////            return;
////        final byte[] bytes = buffer2Array(buffer);
////        new Thread(new Runnable() {
////            @Override
////            public void run() {
////                try {
////                    String filesName = System.currentTimeMillis() + ".jpg";//这个是文件名加后缀
////                    String bgFileName = "background_" + filesName;//背景文件名
////
////                    File backgroundFile = new File(Environment.getExternalStorageDirectory() + File.separator + bgFileName);
////
////                    BitmapFactory.Options newOpts = new BitmapFactory.Options();
////                    newOpts.inJustDecodeBounds = true;
////                    YuvImage yuvimage = new YuvImage(bytes, ImageFormat.NV21, mWidth, mHeight == 1088 ? 1080 : mHeight, null);
////                    ByteArrayOutputStream baos;
////                    byte[] rawImage;
////                    baos = new ByteArrayOutputStream();
////                    yuvimage.compressToJpeg(new Rect(0, 0, mWidth, mHeight == 1088 ? 1080 : mHeight), 100, baos);// 80--JPG图片的质量[0-100],100最高
////                    rawImage = baos.toByteArray();
////
////                    // 将rawImage转换成bitmap
////                    BitmapFactory.Options options = new BitmapFactory.Options();
////                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
////                    Bitmap bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length, options);
////                    FileOutputStream fosImage = new FileOutputStream(backgroundFile);
////                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fosImage);
////                    fosImage.close();
////                    Log.e(TAG, "run: 写入图库" );
////                } catch (Exception e) {
////                    e.printStackTrace();
////                    Log.e(TAG, "run: " + e);
////                }
////            }
////        }).start();
////
////    }
//
//
//    public Bitmap saveYUV2Bitmap(byte[] yuv, int mWidth, int mHeight) {
//        YuvImage image = new YuvImage(yuv, ImageFormat.NV21, mWidth, mHeight, null);
//        ByteArrayOutputStream stream = new ByteArrayOutputStream();
//        image.compressToJpeg(new Rect(0, 0, mWidth, mHeight), 100, stream);
//        Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
//        try {
//            stream.flush();
//            stream.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        return bmp;
//    }
//
//    // https://blog.csdn.net/xiaole0313/article/details/73655889
//    private Bitmap yuvToBitmap(byte[] data, int width, int height) {
//        int frameSize = width * height;
//        int[] rgba = new int[frameSize];
//        for (int i = 0; i < height; i++)
//            for (int j = 0; j < width; j++) {
//                int y = (0xff & ((int) data[i * width + j]));
//                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
//                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
//                y = y < 16 ? 16 : y;
//                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
//                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
//                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));
//                r = r < 0 ? 0 : (r > 255 ? 255 : r);
//                g = g < 0 ? 0 : (g > 255 ? 255 : g);
//                b = b < 0 ? 0 : (b > 255 ? 255 : b);
//                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
//            }
//
//        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        bmp.setPixels(rgba, 0 , width, 0, 0, width, height);
//
//        return bmp;
//    }
//
//    private void saveBitmap(Bitmap bmp, int width, int height) throws IOException {
//        FileOutputStream outStream = null;
//        File file = new File("/mnt/sdcard/AAAA");
//        if(!file.exists()){
//            file.mkdir();
//        }
//
//        try {
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//            outStream = new FileOutputStream(
//                    String.format("/mnt/sdcard/AAAA/%d_%s_%s.jpg",
//                            System.currentTimeMillis(),String.valueOf(width),String.valueOf(height)));
//            bmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
//            outStream.write(baos.toByteArray());
//            outStream.close();
//
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//
//        }
//    }
//
//
//    public void saveYUVtoPicture(byte[] data,int width,int height) throws IOException{
//        FileOutputStream outStream = null;
//        File file = new File("/mnt/sdcard/Camera");
//        if(!file.exists()){
//            file.mkdir();
//        }
//
//        try {
//            YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
//
//
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            yuvimage.compressToJpeg(new Rect(0, 0,width, height), 80, baos);
//
//            Bitmap bmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.toByteArray().length);
//
//            outStream = new FileOutputStream(
//                    String.format("/mnt/sdcard/Camera/%d_%s_%s.jpg",
//                            System.currentTimeMillis(),String.valueOf(width),String.valueOf(height)));
//            bmp.compress(Bitmap.CompressFormat.JPEG, 85, outStream);
//            outStream.write(baos.toByteArray());
//            outStream.close();
//
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//
//        }
//    }
//
////    private byte[] buffer2Array(ByteBuffer buffer) {
////        int limit = buffer.limit();
////        int position = buffer.position();
////        int len = limit - position;
////
////        if (len == 0)
////            return null;
////
////        byte[] bytes = new byte[len];
////        buffer.get(bytes);
////
////        return bytes;
////    }
//
//    private byte[] buffer2Array(ByteBuffer buffer) {
//        byte[] in = new byte[buffer.capacity()];
//
//        buffer.clear();
//        buffer.get(in);
//
//        return in;
//    }
//
//    private void writeToFile(String path, ByteBuffer buffer) {
//        try {
//            FileOutputStream fos = new FileOutputStream(path, true);
//
//            byte[] in = new byte[buffer.capacity()];
//
//            buffer.clear();
//            buffer.get(in);
//
//            fos.write(in);
//            fos.close();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    public static int byteToInt(byte data) {
//        int heightBit = (int) ((data>>4) & 0x0F);
//        int lowBit = (int) (0x0F & data);
//
//        return heightBit * 16 + lowBit;
//    }
//
//    public static int[] byteToColor(byte[] data) {
//        int size = data.length;
//        if (size == 0){
//            return null;
//        }
//
//        int arg = 0;
//        if (size % 3 != 0){
//            arg = 1;
//        }
//
//        int []color = new int[size / 3 + arg];
//        int red, green, blue;
//
//        if (arg == 0){
//            for(int i = 0; i < color.length; ++i){
//                red = byteToInt(data[i * 3]);
//                green = byteToInt(data[i * 3 + 1]);
//                blue = byteToInt(data[i * 3 + 2]);
//
//                color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
//            }
//        }else{
//            for(int i = 0; i < color.length - 1; ++i){
//                red = byteToInt(data[i * 3]);
//                green = byteToInt(data[i * 3 + 1]);
//                blue = byteToInt(data[i * 3 + 2]);
//                color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
//            }
//
//            color[color.length - 1] = 0xFF000000;
//        }
//
//        return color;
//    }
//
//    Bitmap decodeFrameToBitmap(byte[] frame) {
//        int []colors = byteToColor(frame);
//        if (colors == null){
//            return null;
//        }
//        Bitmap bmp = Bitmap.createBitmap(colors, 0, 1280, 1280, 720,Bitmap.Config.ARGB_8888);
//        return bmp;
//    }
}
