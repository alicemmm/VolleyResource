package com.perasia.volleyresource.download;


public interface DownloadStateListener {

    void onDownloadComplete(int id);

    void onDownloadFailed(int id, int errorCode, String errorMessage);

    void onProgress(int id, long totalBytes, long downloadedBytes, int progress);
}
