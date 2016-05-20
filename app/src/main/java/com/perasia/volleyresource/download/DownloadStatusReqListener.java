package com.perasia.volleyresource.download;


public interface DownloadStatusReqListener {

    void onDownloadComplete(DownloadRequest downloadRequest);

    void onDownloadFailed(DownloadRequest downloadRequest, int errCode, String errMsg);

    void onProgress(DownloadRequest downloadRequest, long total, long download, int progress);
}
