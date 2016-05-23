package com.perasia.volleyresource.simpleMultiDownload;


import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.perasia.volleyresource.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class MultiDownloadActivity extends Activity {
    private static final String TAG = MultiDownloadActivity.class.getSimpleName();

    private static final int THREAD_COUNT = 4;

    public static int runningThread = 4;// 记录正在运行的下载文件的线程数

    public int currentProcess = 0;// 下载文件的当前进度

    private Button downloadBtn;

    String basepath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_download);
        downloadBtn = (Button) findViewById(R.id.multi_download_btn);

        basepath = Environment.getExternalStorageDirectory().getPath();


        Log.e(TAG, "basePath=" + basepath);

        downloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download();
            }
        });
    }

    /**
     * download
     */
    private void download() {
        final String path = "http://jsp.jisuoping.com/apk/2016/Yuwan-0.6.16.0-81014.apk";

        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    URL url = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();

                    if (code == 200) {
                        //服务器返回的数据长度
                        int length = conn.getContentLength();

                        Log.e(TAG, "resource length=" + length);

                        RandomAccessFile raf = new RandomAccessFile(basepath + File.separator + "/sdcard/temp.apk", "rwd");
                        raf.setLength(length);

                        raf.close();

                        int blockSize = length / THREAD_COUNT;

                        for (int threadId = 1; threadId <= THREAD_COUNT; threadId++) {
                            int startIndex = (threadId - 1) * blockSize - 1;
                            int endIndex = threadId * blockSize - 1;
                            if (threadId == THREAD_COUNT) {
                                endIndex = length;
                            }

                            Log.e(TAG, "--threadId--" + threadId + "--startIndex--" + startIndex + "--endIndex--" + endIndex);

                            new DownloadThread(path, threadId, startIndex, endIndex).start();
                        }

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    private class DownloadThread extends Thread {
        private int threadId;
        private int startIndex;
        private int endIndex;
        private String path;

        public DownloadThread(String path, int threadId, int startIndex, int endIndex) {
            this.path = path;
            this.threadId = threadId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public void run() {
            super.run();
            try {

                //记录长度，实现断点续传，替换成数据库
                File tempFile = new File(basepath + "/sdcard/" + threadId + ".txt");
                if (tempFile.exists() && tempFile.length() > 0) {
                    FileInputStream fis = new FileInputStream(tempFile);
                    byte[] temp = new byte[1024 * 10];
                    int leng = fis.read(temp);
                    //已经下载的长度
                    String downloadLen = new String(temp, 0, leng);
                    int downloadInt = Integer.parseInt(downloadLen);

                    //设置进度条长度
                    int alreadyDownloadInt = downloadInt - startIndex;
                    currentProcess += alreadyDownloadInt;

                    startIndex = downloadInt;
                    fis.close();
                }

                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                //重要：请求服务器下载部分的文件，指定文件的位置
                conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);
                conn.setConnectTimeout(5000);

                int code = conn.getResponseCode();

                Log.e(TAG, "---thread code---" + code);
                InputStream is = conn.getInputStream();

                RandomAccessFile raf = new RandomAccessFile(basepath + File.separator + "/sdcard/temp.apk", "rwd");

                //随机写文件的时候从哪个位置开始写

                raf.seek(startIndex); //定位文件

                int len = 0;

                byte[] buffer = new byte[1024];

                int total = 0;//记录已经下载的数据长度

                while ((len = is.read(buffer)) != -1) {
                    RandomAccessFile recordFile = new RandomAccessFile(basepath + "/sdcard/" + threadId + ".txt", "rwd");
                    raf.write(buffer, 0, len);
                    total += len;
                    recordFile.write(String.valueOf(startIndex + total).getBytes());
                    recordFile.close();

                    //同步加锁防止混乱
                    synchronized (MultiDownloadActivity.this) {
                        currentProcess += len;//获取当前总进度

                        //更新进度条，发送消息更新百分比

                    }
                }

                is.close();
                raf.close();

                Log.e(TAG, "thread=" + threadId + "---download over");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                threadFinish();
            }
        }


        private synchronized void threadFinish() {
            runningThread--;
            if (runningThread == 0) {
                for (int i = 1; i <= THREAD_COUNT; i++) {// 删除记录下载进度的文件
                    File file = new File(basepath + "/sdcard/" + i + ".txt");
                    file.delete();
                }
            }
        }
    }

}



