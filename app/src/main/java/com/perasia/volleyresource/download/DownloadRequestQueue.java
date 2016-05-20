package com.perasia.volleyresource.download;


import android.os.Handler;
import android.os.Looper;

import java.security.InvalidParameterException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadRequestQueue {

    private Set<DownloadRequest> mCurrentReqs = new HashSet<>();

    private PriorityBlockingQueue<DownloadRequest> mDownloadQueue = new PriorityBlockingQueue<>();

    private DownloadDispatcher[] mDownloadDispatchers;

    private AtomicInteger mSequenceGenerator = new AtomicInteger();

    private CallBackDelivery mDelivery;

    class CallBackDelivery {
        private final Executor mCallBackExecutor;

        public CallBackDelivery(final Handler handler) {
            mCallBackExecutor = new Executor() {
                @Override
                public void execute(Runnable command) {
                    handler.post(command);
                }
            };
        }

        public void postDownloadComplete(final DownloadRequest request) {
            mCallBackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (request.getDownloadListener() != null) {
                        request.getDownloadListener().onDownloadComplete(request.getDownloadId());
                    }

                    if (request.getDownloadStatusListener() != null) {
                        request.getDownloadStatusListener().onDownloadComplete(request);
                    }
                }
            });
        }

        public void PostDownloadFailed(final DownloadRequest request, final int errCode, final String errMsg) {
            mCallBackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (request.getDownloadListener() != null) {
                        request.getDownloadListener().onDownloadFailed(request.getDownloadId(), errCode, errMsg);
                    }
                    if (request.getDownloadStatusListener() != null) {
                        request.getDownloadStatusListener().onDownloadFailed(request, errCode, errMsg);
                    }

                }
            });
        }

        public void postProgressUpdate(final DownloadRequest request, final long totalBytes, final long downloadedBytes, final int progress) {
            mCallBackExecutor.execute(new Runnable() {
                public void run() {
                    if (request.getDownloadListener() != null) {
                        request.getDownloadListener().onProgress(request.getDownloadId(), totalBytes, downloadedBytes, progress);
                    }
                    if (request.getDownloadStatusListener() != null) {
                        request.getDownloadStatusListener().onProgress(request, totalBytes, downloadedBytes, progress);
                    }
                }
            });
        }

    }

    public DownloadRequestQueue() {
        initialize(new Handler(Looper.getMainLooper()));
    }

    public DownloadRequestQueue(int threadPoolSize) {
        initialize(new Handler(Looper.getMainLooper()));
    }

    public DownloadRequestQueue(Handler callbackHandler) throws InvalidParameterException {
        if (callbackHandler == null) {
            throw new InvalidParameterException("callbackHandler must not be null");
        }

        initialize(callbackHandler);
    }

    public void start() {
        stop(); // Make sure any currently running dispatchers are stopped.

        // Create download dispatchers (and corresponding threads) up to the pool size.
        for (int i = 0; i < mDownloadDispatchers.length; i++) {
            DownloadDispatcher downloadDispatcher = new DownloadDispatcher(mDownloadQueue, mDelivery);
            mDownloadDispatchers[i] = downloadDispatcher;
            downloadDispatcher.start();
        }
    }

    int add(DownloadRequest request) {
        int downloadId = getDownloadId();
        // Tag the request as belonging to this queue and add it to the set of current requests.
        request.setDownloadRequestQueue(this);

        synchronized (mCurrentReqs) {
            mCurrentReqs.add(request);
        }

        // Process requests in the order they are added.
        request.setDownloadId(downloadId);
        mDownloadQueue.add(request);

        return downloadId;
    }

    int query(int downloadId) {
        synchronized (mCurrentReqs) {
            for (DownloadRequest request : mCurrentReqs) {
                if (request.getDownloadId() == downloadId) {
                    return request.getDownloadState();
                }
            }
        }
        return DownloadManager.STATUS_NOT_FOUND;
    }

    void cancelAll() {

        synchronized (mCurrentReqs) {
            for (DownloadRequest request : mCurrentReqs) {
                request.cancel();
            }

            // Remove all the requests from the queue.
            mCurrentReqs.clear();
        }
    }

    int cancel(int downloadId) {
        synchronized (mCurrentReqs) {
            for (DownloadRequest request : mCurrentReqs) {
                if (request.getDownloadId() == downloadId) {
                    request.cancel();
                    return 1;
                }
            }
        }

        return 0;
    }

    void finish(DownloadRequest request) {
        if (mCurrentReqs != null) {
            synchronized (mCurrentReqs) {
                mCurrentReqs.remove(request);
            }
        }
    }

    void release() {
        if (mCurrentReqs != null) {
            synchronized (mCurrentReqs) {
                mCurrentReqs.clear();
                mCurrentReqs = null;
            }
        }

        if (mDownloadQueue != null) {
            mDownloadQueue = null;
        }

        if (mDownloadDispatchers != null) {
            stop();

            for (int i = 0; i < mDownloadDispatchers.length; i++) {
                mDownloadDispatchers[i] = null;
            }
            mDownloadDispatchers = null;
        }

    }

    private void initialize(Handler callbackHandler) {
        int processors = Runtime.getRuntime().availableProcessors();
        mDownloadDispatchers = new DownloadDispatcher[processors];
        mDelivery = new CallBackDelivery(callbackHandler);
    }

    /**
     * Stops download dispatchers.
     */
    private void stop() {
        for (int i = 0; i < mDownloadDispatchers.length; i++) {
            if (mDownloadDispatchers[i] != null) {
                mDownloadDispatchers[i].quit();
            }
        }
    }

    /**
     * Gets a sequence number.
     */
    private int getDownloadId() {
        return mSequenceGenerator.incrementAndGet();
    }


}
