package com.perasia.volleyresource;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;

public class GsonRequest<T> extends Request<T> {

    private final Response.Listener<T> mListener;

    private Gson mGson;

    private Class<T> mClass;

    public GsonRequest(int method, String url, Class<T> clazz, Response.Listener<T> listener, Response.ErrorListener errListener) {
        super(method, url, errListener);
        mListener = listener;
        mGson = new Gson();
        mClass = clazz;
    }

    public GsonRequest(String url, Class<T> clazz, Response.Listener<T> listener, Response.ErrorListener errListener) {
        this(Method.GET, url, clazz, listener, errListener);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));
            return Response.success(mGson.fromJson(jsonString, mClass),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        }

    }

    @Override
    protected void deliverResponse(T response) {
        if (mListener != null) {
            mListener.onResponse(response);
        }
    }
}
