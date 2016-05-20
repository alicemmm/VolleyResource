package com.perasia.volleyresource.download;


public class DefalutRetryPolicy implements RetryPolicy {
    private static final String TAG = DefalutRetryPolicy.class.getSimpleName();

    public static final int DEFAULT_TIMEOUT_MS = 5000;

    public static final int DEFAULT_MAX_REYRIES = 1;

    public static final float DEFAULT_BACKOFF_MULT = 1f;

    private int mCurrentTimeoutMs;

    private int mCurrentRetryCount;

    private final int mMaxNumRetries;

    private final float mBackOffMultiplier;

    public DefalutRetryPolicy() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_REYRIES, DEFAULT_BACKOFF_MULT);
    }

    public DefalutRetryPolicy(int initTimeOutMs, int maxNumRetries, float backOffMultiplier) {
        mCurrentTimeoutMs = initTimeOutMs;
        mMaxNumRetries = maxNumRetries;
        mBackOffMultiplier = backOffMultiplier;
    }

    @Override
    public int getCurrentTimeout() {
        return mCurrentTimeoutMs;
    }

    @Override
    public int getCurrentRetryCount() {
        return mCurrentRetryCount;
    }

    @Override
    public float getBackOffMultiplier() {
        return mBackOffMultiplier;
    }

    @Override
    public void retry() throws RetryError {
        mCurrentRetryCount++;
        mCurrentTimeoutMs += (mCurrentTimeoutMs * mBackOffMultiplier);
        if (!hasAttemptRemaining()) {
            throw new RetryError();
        }
    }

    protected boolean hasAttemptRemaining() {
        return mCurrentRetryCount <= mMaxNumRetries;
    }
}
