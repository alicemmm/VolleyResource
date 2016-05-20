package com.perasia.volleyresource.download;


public interface RetryPolicy {

    int getCurrentTimeout();

    int getCurrentRetryCount();

    float getBackOffMultiplier();

    void retry() throws RetryError;
}
