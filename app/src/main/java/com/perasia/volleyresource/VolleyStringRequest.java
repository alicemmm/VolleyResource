package com.perasia.volleyresource;


import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.UnsupportedEncodingException;

/**
 * A canned request for retrieving the response body at a given URL as a String.
 */
public class VolleyStringRequest extends Request<String> {
    private Response.Listener<String> mListener;

    /**
     * Creates a new request with the given method.
     *
     * @param method        the request {@link Method} to use
     * @param url           URL to fetch the string at
     * @param listener      Listener to receive the String response
     * @param errorListener Error listener, or null to ignore errors
     */
    public VolleyStringRequest(int method, String url, Response.Listener<String> listener,
                               Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        mListener = listener;
    }

    /**
     * Creates a new GET request.
     *
     * @param url           URL to fetch the string at
     * @param listener      Listener to receive the String response
     * @param errorListener Error listener, or null to ignore errors
     */
    public VolleyStringRequest(String url, Response.Listener<String> listener, Response.ErrorListener errorListener) {
        this(Method.GET, url, listener, errorListener);
    }

    @Override
    protected void onFinish() {
        super.onFinish();
        mListener = null;
    }

    @Override
    protected void deliverResponse(String response) {
        if (mListener != null) {
            mListener.onResponse(response);
        }
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String parsed;
        try {
            parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
        } catch (UnsupportedEncodingException e) {
            parsed = new String(response.data);
        }
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }
}
