package com.perasia.volleyresource.download;


import android.os.Process;
import android.util.Log;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;

public class DownloadDispatcher extends Thread {
    private static final String TAG = DownloadDispatcher.class.getSimpleName();

    private final BlockingQueue<DownloadRequest> mQueue;

    private volatile boolean mQuit;

    private DownloadRequest mRequest;

    private DownloadRequestQueue.CallBackDelivery mDelivery;

    public final int BUFFER_SIZE = 4096;

    private int mRedirectionCount = 0;

    public final int MAX_REDIRECTS = 4;

    private final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    private final int HTTP_TEMP_REDIRECT = 307;

    private long mContentLength;
    private long mCurrentBytes;
    boolean shouldAllowRedirects = true;

    Timer mTimer;

    public DownloadDispatcher(BlockingQueue<DownloadRequest> queue, DownloadRequestQueue.CallBackDelivery delivery) {
        mQueue = queue;
        mDelivery = delivery;
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        mTimer = new Timer();
        while (true) {
            try {
                mRequest = mQueue.take();
                mRedirectionCount = 0;
                Log.v(TAG, "Download initiated for " + mRequest.getDownloadId());

                updateDownloadState(DownloadManager.STATUS_STARTED);
                executeDownload(mRequest.getUri().toString().trim());
            } catch (InterruptedException e) {
                if (mQuit) {
                    if (mRequest != null) {
                        mRequest.finish();
                        updateDownloadFailed(DownloadManager.ERROR_DOWNLOAD_CANCELLED, "Download cancelled");
                        mTimer.cancel();
                    }

                    return;
                }

                continue;
            }
        }
    }

    public void quit() {
        mQuit = true;
        interrupt();
    }

    private void executeDownload(String downloadUrl) {
        URL url;
        try {
            url = new URL(downloadUrl);
        } catch (MalformedURLException e) {
            updateDownloadFailed(DownloadManager.ERROR_MALFORMED_URI,"MalformedURLException: URI passed is malformed.");
            return;
        }

        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(mRequest.getRetryPolicy().getCurrentTimeout());
            conn.setReadTimeout(mRequest.getRetryPolicy().getCurrentTimeout());

            HashMap<String, String> customHeaders = mRequest.getCustomHeader();
            if (customHeaders != null) {
                for (String headerName : customHeaders.keySet()) {
                    conn.addRequestProperty(headerName, customHeaders.get(headerName));
                }
            }

            // Status Connecting is set here before
            // urlConnection is trying to connect to destination.
            updateDownloadState(DownloadManager.STATUS_CONNECTING);

            final int responseCode = conn.getResponseCode();

            Log.v(TAG, "Response code obtained for downloaded Id "
                    + mRequest.getDownloadId()
                    + " : httpResponse Code "
                    + responseCode);

            switch (responseCode) {
                case HttpURLConnection.HTTP_PARTIAL:
                case HttpURLConnection.HTTP_OK:
                    shouldAllowRedirects = false;
                    if (readResponseHeaders(conn) == 1) {
                        transferData(conn);
                    } else {
                        updateDownloadFailed(DownloadManager.ERROR_DOWNLOAD_SIZE_UNKNOWN, "Transfer-Encoding not found as well as can't know size of download, giving up");
                    }
                    return;
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_MOVED_TEMP:
                case HttpURLConnection.HTTP_SEE_OTHER:
                case HTTP_TEMP_REDIRECT:
                    // Take redirect url and call executeDownload recursively until
                    // MAX_REDIRECT is reached.
                    while (mRedirectionCount++ < MAX_REDIRECTS && shouldAllowRedirects) {
                        Log.v(TAG, "Redirect for downloaded Id "+mRequest.getDownloadId());
                        final String location = conn.getHeaderField("Location");
                        executeDownload(location);
                        continue;
                    }

                    if (mRedirectionCount > MAX_REDIRECTS) {
                        updateDownloadFailed(DownloadManager.ERROR_TOO_MANY_REDIRECTS, "Too many redirects, giving up");
                        return;
                    }
                    break;
                case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
                    updateDownloadFailed(HTTP_REQUESTED_RANGE_NOT_SATISFIABLE, conn.getResponseMessage());
                    break;
                case HttpURLConnection.HTTP_UNAVAILABLE:
                    updateDownloadFailed(HttpURLConnection.HTTP_UNAVAILABLE, conn.getResponseMessage());
                    break;
                case HttpURLConnection.HTTP_INTERNAL_ERROR:
                    updateDownloadFailed(HttpURLConnection.HTTP_INTERNAL_ERROR, conn.getResponseMessage());
                    break;
                default:
                    updateDownloadFailed(DownloadManager.ERROR_UNHANDLED_HTTP_CODE, "Unhandled HTTP response:" + responseCode +" message:" +conn.getResponseMessage());
                    break;
            }
        } catch(SocketTimeoutException e) {
            e.printStackTrace();
            // Retry.
            attemptRetryOnTimeOutException();
        } catch (ConnectTimeoutException e) {
            e.printStackTrace();
            attemptRetryOnTimeOutException();
        } catch(IOException e) {
            e.printStackTrace();
            updateDownloadFailed(DownloadManager.ERROR_HTTP_DATA_ERROR, "Trouble with low-level sockets");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void transferData(HttpURLConnection conn) {
        InputStream in = null;
        OutputStream out = null;
        FileDescriptor outFd = null;
        cleanupDestination();
        try {
            try {
                in = conn.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            File destinationFile = new File(mRequest.getDestinationUri().getPath().toString());

            boolean errorCreatingDestinationFile = false;
            // Create destination file if it doesn't exists
            if (destinationFile.exists() == false) {
                try {
                    if (destinationFile.createNewFile() == false) {
                        errorCreatingDestinationFile = true;
                        updateDownloadFailed(DownloadManager.ERROR_FILE_ERROR,
                                "Error in creating destination file");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    errorCreatingDestinationFile = true;
                    updateDownloadFailed(DownloadManager.ERROR_FILE_ERROR,
                            "Error in creating destination file");
                }
            }

            // If Destination file couldn't be created. Abort the data transfer.
            if (errorCreatingDestinationFile == false) {
                try {
                    out = new FileOutputStream(destinationFile, true);
                    outFd = ((FileOutputStream) out).getFD();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (in == null) {
                    updateDownloadFailed(DownloadManager.ERROR_FILE_ERROR,
                            "Error in creating input stream");
                } else if (out == null) {

                    updateDownloadFailed(DownloadManager.ERROR_FILE_ERROR,
                            "Error in writing download contents to the destination file");
                } else {
                    // Start streaming data
                    transferData(in, out);
                }
            }

        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                if (out != null) {
                    out.flush();
                }
                if (outFd != null) {
                    outFd.sync();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void transferData(InputStream in, OutputStream out) {
        final byte data[] = new byte[BUFFER_SIZE];
        mCurrentBytes = 0;
        mRequest.setDownloadState(DownloadManager.STATUS_RUNNING);
        Log.v(TAG, "Content Length: " + mContentLength + " for Download Id " + mRequest.getDownloadId());
        for (;;) {
            if (mRequest.isCancelled()) {
                Log.v(TAG, "Stopping the download as Download Request is cancelled for Downloaded Id "+mRequest.getDownloadId());
                mRequest.finish();
                updateDownloadFailed(DownloadManager.ERROR_DOWNLOAD_CANCELLED, "Download cancelled");
                return;
            }
            int bytesRead = readFromResponse( data, in);

            if (mContentLength != -1 && mContentLength > 0) {
                int progress = (int) ((mCurrentBytes * 100) / mContentLength);
                updateDownloadProgress(progress, mCurrentBytes);
            }

            if (bytesRead == -1) { // success, end of stream already reached
                updateDownloadComplete();
                return;
            } else if (bytesRead == Integer.MIN_VALUE) {
                return;
            }

            if (writeDataToDestination(data, bytesRead, out)) {
                mCurrentBytes += bytesRead;
            }
        }
    }

    private int readFromResponse( byte[] data, InputStream entityStream) {
        try {
            return entityStream.read(data);
        } catch (IOException ex) {
            if ("unexpected end of stream".equals(ex.getMessage())) {
                return -1;
            }
            updateDownloadFailed(DownloadManager.ERROR_HTTP_DATA_ERROR, "IOException: Failed reading response");
            return Integer.MIN_VALUE;
        }
    }

    private boolean writeDataToDestination(byte[] data, int bytesRead, OutputStream out) {
        boolean successInWritingToDestination = true;
        try {
            out.write(data, 0, bytesRead);
        } catch (IOException ex) {
            updateDownloadFailed(DownloadManager.ERROR_FILE_ERROR, "IOException when writing download contents to the destination file");
            successInWritingToDestination = false;
        } catch (Exception e) {
            updateDownloadFailed(DownloadManager.ERROR_FILE_ERROR, "Exception when writing download contents to the destination file");
            successInWritingToDestination = false;
        }

        return successInWritingToDestination;
    }

    private int readResponseHeaders( HttpURLConnection conn){
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        mContentLength = -1;

        if (transferEncoding == null) {
            mContentLength = getHeaderFieldLong(conn, "Content-Length", -1);
        } else {
            Log.v(TAG, "Ignoring Content-Length since Transfer-Encoding is also defined for Downloaded Id " + mRequest.getDownloadId());
        }

        if (mContentLength != -1) {
            return 1;
        } else if(transferEncoding == null || !transferEncoding.equalsIgnoreCase("chunked")) {
            return -1;
        } else {
            return 1;
        }
    }

    public long getHeaderFieldLong(URLConnection conn, String field, long defaultValue) {
        try {
            return Long.parseLong(conn.getHeaderField(field));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void attemptRetryOnTimeOutException()  {
        updateDownloadState(DownloadManager.STATUS_RETRYING);
        final RetryPolicy retryPolicy = mRequest.getRetryPolicy();
        try {
            retryPolicy.retry();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    executeDownload(mRequest.getUri().toString());
                }
            }, retryPolicy.getCurrentTimeout());
        } catch (RetryError e) {
            // Update download failed.
            updateDownloadFailed(DownloadManager.ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES,
                    "Connection time out after maximum retires attempted");
        }
    }

    /**
     * Called just before the thread finishes, regardless of status, to take any necessary action on
     * the downloaded file.
     */
    private void cleanupDestination() {
        Log.d(TAG, "cleanupDestination() deleting " + mRequest.getDestinationUri().getPath());
        File destinationFile = new File(mRequest.getDestinationUri().getPath());
        if(destinationFile.exists()) {
            destinationFile.delete();
        }
    }

    public void updateDownloadState(int state) {
        mRequest.setDownloadState(state);
    }

    public void updateDownloadComplete() {
        mDelivery.postDownloadComplete(mRequest);
        mRequest.setDownloadState(DownloadManager.STATUS_SUCCESSFUL);
        mRequest.finish();
    }

    public void updateDownloadFailed(int errorCode, String errorMsg) {
        shouldAllowRedirects = false;
        mRequest.setDownloadState(DownloadManager.STATUS_FAILED);
        if(mRequest.isDeleteDestinationFileOnFailure()) {
            cleanupDestination();
        }
        mDelivery.PostDownloadFailed(mRequest, errorCode, errorMsg);
        mRequest.finish();
    }

    public void updateDownloadProgress(int progress, long downloadedBytes) {
        mDelivery.postProgressUpdate(mRequest,mContentLength, downloadedBytes, progress);
    }

}
