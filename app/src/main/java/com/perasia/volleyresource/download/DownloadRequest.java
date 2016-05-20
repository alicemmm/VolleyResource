package com.perasia.volleyresource.download;


import android.app.DownloadManager;
import android.net.Uri;

import java.util.HashMap;

public class DownloadRequest implements Comparable<DownloadRequest> {

    public enum Priority {
        LOW, NORMAL, HIGH, IMMEDIATE
    }

    private int mDownloadState;

    private int mDownloadId;

    private Uri mUri;

    private Uri mDestinationUri;

    private RetryPolicy mRetryPolicy;

    private boolean mCancelled = false;

    private boolean mDeleteDestinationFileOnFailure = true;

    private DownloadRequestQueue mRequestQueue;

    private DownloadStateListener mDownloadListener;

    private DownloadStatusReqListener mDownloadStatusListener;

    private Object mDownloadContext;

    private HashMap<String, String> mCustomHeader;

    private Priority mPriority = Priority.NORMAL;

    public DownloadRequest(Uri uri) {
        if (uri == null) {
            throw new NullPointerException();
        }

        String scheme = uri.getScheme();

        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalArgumentException("Can only download HTTP/HTTPS URIs:" + uri);
        }

        mCustomHeader = new HashMap<>();

        mDownloadState = DownloadManager.STATUS_PENDING;

        mUri = uri;
    }

    public Priority getPriority() {
        return mPriority;
    }

    public DownloadRequest setPriority(Priority priority) {
        this.mPriority = priority;
        return this;
    }

    public DownloadRequest addCustomHeader(String key, String value) {
        mCustomHeader.put(key, value);
        return this;
    }

    void setDownloadRequestQueue(DownloadRequestQueue downloadQueue) {
        mRequestQueue = downloadQueue;
    }

    public RetryPolicy getRetryPolicy() {
        return mRetryPolicy == null ? new DefalutRetryPolicy() : mRetryPolicy;
    }

    public DownloadRequest setRetryPolicy(RetryPolicy retryPolicy) {
        this.mRetryPolicy = retryPolicy;
        return this;
    }

    public final int getDownloadId() {
        return mDownloadId;
    }

    final public void setDownloadId(int downloadId) {
        this.mDownloadId = downloadId;
    }

    public int getDownloadState() {
        return mDownloadState;
    }

    public void setDownloadState(int downloadState) {
        this.mDownloadState = downloadState;
    }

    DownloadStateListener getDownloadListener() {
        return mDownloadListener;
    }

    public DownloadRequest setDownloadListener(DownloadStateListener downloadListener) {
        this.mDownloadListener = downloadListener;
        return this;
    }

    public DownloadStatusReqListener getDownloadStatusListener() {
        return mDownloadStatusListener;
    }

    public DownloadRequest setDownloadStatusListener(DownloadStatusReqListener downloadStatusListener) {
        this.mDownloadStatusListener = downloadStatusListener;
        return this;
    }

    public Object getDownloadContext() {
        return mDownloadContext;
    }

    public DownloadRequest setDownloadContext(Object downloadContext) {
        this.mDownloadContext = downloadContext;
        return this;
    }

    public Uri getUri() {
        return mUri;
    }

    public DownloadRequest setUri(Uri uri) {
        this.mUri = uri;
        return this;
    }

    public Uri getDestinationUri() {
        return mDestinationUri;
    }

    public DownloadRequest setDestinationUri(Uri destinationUri) {
        this.mDestinationUri = destinationUri;
        return this;
    }

    public boolean isDeleteDestinationFileOnFailure() {
        return mDeleteDestinationFileOnFailure;
    }

    public DownloadRequest setDeleteDestinationFileOnFailure(boolean deleteOnFailure) {
        this.mDeleteDestinationFileOnFailure = deleteOnFailure;
        return this;
    }

    public boolean isCancelled() {
        return mCancelled;
    }

    public void cancel() {
        this.mCancelled = true;
    }

    HashMap<String,String> getCustomHeader(){
        return mCustomHeader;
    }

    void finish(){
        mRequestQueue.finish(this);
    }

    @Override
    public int compareTo(DownloadRequest another) {
        Priority left = this.getPriority();
        Priority right = another.getPriority();

        return left == right ?
                this.mDownloadId - another.mDownloadId :
                right.ordinal() - left.ordinal();
    }
}
