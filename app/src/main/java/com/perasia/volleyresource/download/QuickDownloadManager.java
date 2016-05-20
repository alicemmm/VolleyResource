package com.perasia.volleyresource.download;


import android.os.Handler;

import java.security.InvalidParameterException;

public class QuickDownloadManager implements DownloadManager {

    private DownloadRequestQueue mRequestQueue;

    public QuickDownloadManager() {
        mRequestQueue = new DownloadRequestQueue();
        mRequestQueue.start();
    }

    public QuickDownloadManager(Handler callbackHandler) throws InvalidParameterException {
        mRequestQueue = new DownloadRequestQueue(callbackHandler);
        mRequestQueue.start();
    }

    public QuickDownloadManager(int threadPoolSize) {
        mRequestQueue = new DownloadRequestQueue(threadPoolSize);
        mRequestQueue.start();
    }

    @Override
    public int add(DownloadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("DownloadRequest cannot be null");
        }

        return mRequestQueue.add(request);
    }

    @Override
    public int cancel(int downloadId) {
        return mRequestQueue.cancel(downloadId);
    }

    @Override
    public void cancelAll() {
        mRequestQueue.cancelAll();
    }

    @Override
    public int query(int downloadId) {
        return mRequestQueue.query(downloadId);
    }

    @Override
    public void release() {
        if (mRequestQueue != null) {
            mRequestQueue.release();
            mRequestQueue = null;
        }

    }
}
