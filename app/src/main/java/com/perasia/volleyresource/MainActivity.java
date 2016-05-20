package com.perasia.volleyresource;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.StringRequest;
import com.perasia.volleyresource.download.DefalutRetryPolicy;
import com.perasia.volleyresource.download.DownloadManager;
import com.perasia.volleyresource.download.DownloadRequest;
import com.perasia.volleyresource.download.DownloadStatusReqListener;
import com.perasia.volleyresource.download.QuickDownloadManager;
import com.perasia.volleyresource.download.RetryPolicy;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private NetworkImageView imageView;

    int downloadId1;

    private Button button;

    ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (NetworkImageView) findViewById(R.id.network_image_view);
        progressBar = (ProgressBar) findViewById(R.id.progressbar);
        button = (Button) findViewById(R.id.download_btn);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quickDownload();
                button.setClickable(false);
            }
        });

//        volleyRequest();

//        gsonRequest();
    }


    private void quickDownload() {
        String url = "http://jsp.jisuoping.com/apk/2016/Yuwan-0.6.16.0-81014.apk";
        QuickDownloadManager downloadManager = new QuickDownloadManager();
        RetryPolicy retryPolicy = new DefalutRetryPolicy();

        File filesDir = getExternalCacheDir();
        Uri downloadUri = Uri.parse(url);
        Uri destinationUri = Uri.parse(filesDir + "/test_81014.apk");
        final DownloadRequest downloadRequest1 = new DownloadRequest(downloadUri)
                .setDestinationUri(destinationUri).setPriority(DownloadRequest.Priority.LOW)
                .setRetryPolicy(retryPolicy)
                .setDownloadContext("Download1")
                .setDownloadStatusListener(new DownloadStatusReqListener() {
                    @Override
                    public void onDownloadComplete(DownloadRequest downloadRequest) {
                        Log.e(TAG, "onDownloadComplete");
                    }

                    @Override
                    public void onDownloadFailed(DownloadRequest downloadRequest, int errCode, String errMsg) {
                        Log.e(TAG, "onDownloadFailed" + errMsg);
                    }

                    @Override
                    public void onProgress(DownloadRequest downloadRequest, long total, long download, int progress) {
                        float pro = (float) download;
                        progressBar.setProgress(progress);
                        Log.e(TAG, "onProgress=" + pro / total + "%----" + progress);
                    }
                });

        if (downloadManager.query(downloadId1) == DownloadManager.STATUS_NOT_FOUND) {
            downloadId1 = downloadManager.add(downloadRequest1);
        }

    }


    private void volleyRequest() {
        String url = "http://www.baidu.com";

        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.e(TAG, "response=" + response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "error=" + error);
            }
        });

        requestQueue.add(request);
    }

    private void volleyPost() {
        String url = "http://www.baidu.com";

        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        StringRequest request = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<String, String>();
                map.put("params1", "value1");
                map.put("params2", "value2");
                return map;
            }
        };

        requestQueue.add(request);
    }

    private void jsonRequest() {
        String url = "http://m.weather.com.cn/data/101010100.html";

        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        JsonObjectRequest request = new JsonObjectRequest(url, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        requestQueue.add(request);
    }

    private void ImageRequest() {
        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        String url = "http://source.jisuoping.com/image/20160509172732830.jpg";

        // url,callback ,width,height,颜色属性

        ImageRequest imageRequest = new ImageRequest(url, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {

            }
        }, 0, 0, Bitmap.Config.RGB_565, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        requestQueue.add(imageRequest);
    }

    private void imageLoadger() {
        String url = "http://source.jisuoping.com/image/20160509172732830.jpg";

        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        ImageLoader imageLoader = new ImageLoader(requestQueue, new BitmapCache());

        ImageView imageView = new ImageView(this);
        ImageLoader.ImageListener listener = ImageLoader.getImageListener(imageView,
                R.mipmap.ic_launcher, R.mipmap.ic_launcher);
//        ImageLoader.ImageListener listener = ImageLoader.getImageListener(imageView,
//                R.mipmap.ic_launcher, R.mipmap.ic_launcher,200,200);

        imageLoader.get(url, listener);
    }

    public static class BitmapCache implements ImageLoader.ImageCache {

        private LruCache<String, Bitmap> mCache;

        public BitmapCache() {
            int maxSize = 10 * 1024 * 1024;
            mCache = new LruCache<String, Bitmap>(maxSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getRowBytes() * bitmap.getHeight();
                }
            };
        }

        @Override
        public Bitmap getBitmap(String url) {
            return mCache.get(url);
        }

        @Override
        public void putBitmap(String url, Bitmap bitmap) {
            mCache.put(url, bitmap);
        }
    }

    private void netWorkImageView() {
        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        ImageLoader imageLoader = new ImageLoader(requestQueue, new BitmapCache());

        imageView.setDefaultImageResId(R.mipmap.ic_launcher);
        imageView.setErrorImageResId(R.mipmap.ic_launcher);
        imageView.setImageUrl("http://img.my.csdn.net/uploads/201404/13/1397393290_5765.jpeg",
                imageLoader);
    }

    private void myXMLRequest() {
        String url = "http://flash.weather.com.cn/wmaps/xml/china.xml";

        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        XMLRequest xmlRequest = new XMLRequest(Request.Method.GET, url,
                new Response.Listener<XmlPullParser>() {
                    @Override
                    public void onResponse(XmlPullParser response) {
                        try {
                            int eventType = response.getEventType();
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                switch (eventType) {
                                    case XmlPullParser.START_TAG:
                                        String nodeName = response.getName();
                                        if ("city".equals(nodeName)) {
                                            String pName = response.getAttributeValue(0);
                                            Log.d("TAG", "pName is " + pName);
                                        }
                                        break;
                                }
                                eventType = response.next();
                            }
                        } catch (XmlPullParserException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("TAG", error.getMessage(), error);
            }
        });

        requestQueue.add(xmlRequest);
    }

    private void gsonRequest() {
        String url = "http://www.weather.com.cn/data/sk/101010100.html";

        RequestQueue requestQueue = VolleyManager.getRequestQueue();

        GsonRequest<Weather> weatherGsonRequest = new GsonRequest<Weather>(url, Weather.class,
                new Response.Listener<Weather>() {
                    @Override
                    public void onResponse(Weather weather) {
                        WeatherInfo weatherInfo = weather.getWeatherinfo();
                        Log.e("TAG", "city is " + weatherInfo.getCity());
                        Log.e("TAG", "temp is " + weatherInfo.getTemp());
                        Log.e("TAG", "time is " + weatherInfo.getTime());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        requestQueue.add(weatherGsonRequest);
    }
}
