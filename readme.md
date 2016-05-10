#Volley网络框架扩展及源码分析

1、首先volley初始化<br><br>

        <public static RequestQueue newRequestQueue(Context context) {
             return newRequestQueue(context, null);
         } >
***

volley初始化代码


        <public static RequestQueue newRequestQueue(Context context, HttpStack stack, int maxDiskCacheBytes) {
                 File cacheDir = new File(context.getCacheDir(), DEFAULT_CACHE_DIR);

                 String userAgent = "volley/0";
                 try {
                     String packageName = context.getPackageName();
                     PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
                     userAgent = packageName + "/" + info.versionCode;
                 } catch (NameNotFoundException e) {
                 }

                 if (stack == null) {
                     if (Build.VERSION.SDK_INT >= 9) {
                         stack = new HurlStack();
                     } else {
                         // Prior to Gingerbread, HttpUrlConnection was unreliable.
                         // See: http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                         stack = new HttpClientStack(AndroidHttpClient.newInstance(userAgent));
                     }
                 }

                 Network network = new BasicNetwork(stack);

                 RequestQueue queue;
                 if (maxDiskCacheBytes <= -1)
                 {
                 	// No maximum size specified
                 	queue = new RequestQueue(new DiskBasedCache(cacheDir), network);
                 }
                 else
                 {
                 	// Disk cache size specified
                 	queue = new RequestQueue(new DiskBasedCache(cacheDir, maxDiskCacheBytes), network);
                 }

                 queue.start();

                 return queue;
             }>

我们发现24行判断stack是空，就创建一个HttpStack对象，这里根据版本号创建不同的实例，查看源码，发现HurlStack内部<br>
是使用HttpURLConnection进行网络通信，HttpClientStack内部则是使用HttpClient进行网络通信。<br>

之后又创建了Network对象，它是用于根据传入的HttpStack对象来处理网络请求的。

紧接着根据是否设置最大缓存创建一个请求队列，并调用start()方法，然后返回这个队列。

我们看一下start方法都干了什么：

        <public void start() {
                stop();  // Make sure any currently running dispatchers are stopped.
                // Create the cache dispatcher and start it.
                mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
                mCacheDispatcher.start();

                // Create network dispatchers (and corresponding threads) up to the pool size.
                for (int i = 0; i < mDispatchers.length; i++) {
                    NetworkDispatcher networkDispatcher = new NetworkDispatcher(mNetworkQueue, mNetwork,
                            mCache, mDelivery);
                    mDispatchers[i] = networkDispatcher;
                    networkDispatcher.start();
                }
            }>

这里先是创建了一个CacheDispatcher的实例，然后调用了它的start()方法，接着在一个for循环里去创建NetworkDispatcher的实例，<br>
并分别调用它们的start()方法。这里的CacheDispatcher和NetworkDispatcher都是继承自Thread的，而默认情况下for循环会执行四次，<br>
也就是说当调用了Volley.newRequestQueue(context)之后，就会有五个线程一直在后台运行，不断等待网络请求的到来，<br>
其中CacheDispatcher是缓存线程，NetworkDispatcher是网络请求线程。

我们进行网络请求的时候，是把各种request add到RequestQueue请求队列里面，进行网络请求。
我们来看一下add()方法里面写的什么。

        < public <T> Request<T> add(Request<T> request) {
                 // Tag the request as belonging to this queue and add it to the set of current requests.
                 request.setRequestQueue(this);
                 synchronized (mCurrentRequests) {
                     mCurrentRequests.add(request);
                 }

                 // Process requests in the order they are added.
                 request.setSequence(getSequenceNumber());
                 request.addMarker("add-to-queue");

                 // If the request is uncacheable, skip the cache queue and go straight to the network.
                 if (!request.shouldCache()) {
                     mNetworkQueue.add(request);
                     return request;
                 }

                 // Insert request into stage if there's already a request with the same cache key in flight.
                 synchronized (mWaitingRequests) {
                     String cacheKey = request.getCacheKey();
                     if (mWaitingRequests.containsKey(cacheKey)) {
                         // There is already a request in flight. Queue up.
                         Queue<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);
                         if (stagedRequests == null) {
                             stagedRequests = new LinkedList<Request<?>>();
                         }
                         stagedRequests.add(request);
                         mWaitingRequests.put(cacheKey, stagedRequests);
                         if (VolleyLog.DEBUG) {
                             VolleyLog.v("Request for cacheKey=%s is in flight, putting on hold.", cacheKey);
                         }
                     } else {
                         // Insert 'null' queue for this cacheKey, indicating there is now a request in
                         // flight.
                         mWaitingRequests.put(cacheKey, null);
                         mCacheQueue.add(request);
                     }
                     return request;
                 }
             }
        >

可以看出，97行，判断该请求是否可以缓存，若可以缓存了就加入队列，不能缓存在120行加入缓存队列。默认情况下所有的请求<br>
都会加入缓存队列，我们可以调用Request的setShouldCache(false)方法来改变这一默认行为。

当请求添加到缓存队列，我们就要看在后台运行的缓存线程在做了什么

        <public class CacheDispatcher extends Thread {

             ...

             @Override
             public void run() {
                 if (DEBUG) VolleyLog.v("start new dispatcher");
                 Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

                 // Make a blocking call to initialize the cache.
                 mCache.initialize();

                 Request<?> request;
                 while (true) {
                     // release previous request object to avoid leaking request object when mQueue is drained.
                     request = null;
                     try {
                         // Take a request from the queue.
                         request = mCacheQueue.take();
                     } catch (InterruptedException e) {
                         // We may have been interrupted because it was time to quit.
                         if (mQuit) {
                             return;
                         }
                         continue;
                     }
                     try {
                         request.addMarker("cache-queue-take");

                         // If the request has been canceled, don't bother dispatching it.
                         if (request.isCanceled()) {
                             request.finish("cache-discard-canceled");
                             continue;
                         }

                         // Attempt to retrieve this item from cache.
                         Cache.Entry entry = mCache.get(request.getCacheKey());
                         if (entry == null) {
                             request.addMarker("cache-miss");
                             // Cache miss; send off to the network dispatcher.
                             mNetworkQueue.put(request);
                             continue;
                         }

                         // If it is completely expired, just send it to the network.
                         if (entry.isExpired()) {
                             request.addMarker("cache-hit-expired");
                             request.setCacheEntry(entry);
                             mNetworkQueue.put(request);
                             continue;
                         }

                         // We have a cache hit; parse its data for delivery back to the request.
                         request.addMarker("cache-hit");
                         Response<?> response = request.parseNetworkResponse(
                                 new NetworkResponse(entry.data, entry.responseHeaders));
                         request.addMarker("cache-hit-parsed");

                         if (!entry.refreshNeeded()) {
                             // Completely unexpired cache hit. Just deliver the response.
                             mDelivery.postResponse(request, response);
                         } else {
                             // Soft-expired cache hit. We can deliver the cached response,
                             // but we need to also send the request to the network for
                             // refreshing.
                             request.addMarker("cache-hit-refresh-needed");
                             request.setCacheEntry(entry);

                             // Mark the response as intermediate.
                             response.intermediate = true;

                             // Post the intermediate response back to the user and have
                             // the delivery then forward the request along to the network.
                             final Request<?> finalRequest = request;
                             mDelivery.postResponse(request, response, new Runnable() {
                                 @Override
                                 public void run() {
                                     try {
                                         mNetworkQueue.put(finalRequest);
                                     } catch (InterruptedException e) {
                                         // Not much we can do about this.
                                     }
                                 }
                             });
                         }
                     } catch (Exception e) {
                         VolleyLog.e(e, "Unhandled exception %s", e.toString());
                     }
                 }
             }
         }
        >

我们只看run方法里面的代码，在一个无限循环中，第168行试图取出某个响应结果，如果取不到，就添加到网络请求队列，往下<br>
如果取出了响应结构，就会判断该消息是否过期，过期了就加入到网络请求队列，否则直接使用缓存中请求结果信息。<br>
Response<?> response = request.parseNetworkResponse()方法来对数据进行解析.<br>

下面看看请求网络线程在做什么：

        <public class NetworkDispatcher extends Thread {

            ...

             @Override
             public void run() {
                 Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                 Request<?> request;
                 while (true) {
                     long startTimeMs = SystemClock.elapsedRealtime();
                     // release previous request object to avoid leaking request object when mQueue is drained.
                     request = null;
                     try {
                         // Take a request from the queue.
                         request = mQueue.take();
                     } catch (InterruptedException e) {
                         // We may have been interrupted because it was time to quit.
                         if (mQuit) {
                             return;
                         }
                         continue;
                     }

                     try {
                         request.addMarker("network-queue-take");

                         // If the request was cancelled already, do not perform the
                         // network request.
                         if (request.isCanceled()) {
                             request.finish("network-discard-cancelled");
                             continue;
                         }

                         addTrafficStatsTag(request);

                         // Perform the network request.
                         NetworkResponse networkResponse = mNetwork.performRequest(request);
                         request.addMarker("network-http-complete");

                         // If the server returned 304 AND we delivered a response already,
                         // we're done -- don't deliver a second identical response.
                         if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                             request.finish("not-modified");
                             continue;
                         }

                         // Parse the response here on the worker thread.
                         Response<?> response = request.parseNetworkResponse(networkResponse);
                         request.addMarker("network-parse-complete");

                         // Write to cache if applicable.
                         // TODO: Only update cache metadata instead of entire record for 304s.
                         if (request.shouldCache() && response.cacheEntry != null) {
                             mCache.put(request.getCacheKey(), response.cacheEntry);
                             request.addMarker("network-cache-written");
                         }

                         // Post the response back.
                         request.markDelivered();
                         mDelivery.postResponse(request, response);
                     } catch (VolleyError volleyError) {
                         volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
                         parseAndDeliverNetworkError(request, volleyError);
                     } catch (Exception e) {
                         VolleyLog.e(e, "Unhandled exception %s", e.toString());
                         VolleyError volleyError = new VolleyError(e);
                         volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
                         mDelivery.postError(request, volleyError);
                     }
                 }
             }
         }
        >

同样维持一个死循环，267行 NetworkResponse networkResponse = mNetwork.performRequest(request);<br>
进行了网络请求，因为Network是一个接口，所以具体实现是在BasicNetwork。

        <public class BasicNetwork implements Network {

             ...

             @Override
             public NetworkResponse performRequest(Request<?> request) throws VolleyError {
                 long requestStart = SystemClock.elapsedRealtime();
                 while (true) {
                     HttpResponse httpResponse = null;
                     byte[] responseContents = null;
                     Map<String, String> responseHeaders = Collections.emptyMap();
                     try {
                         // Gather headers.
                         Map<String, String> headers = new HashMap<String, String>();
                         addCacheHeaders(headers, request.getCacheEntry());
                         httpResponse = mHttpStack.performRequest(request, headers);
                         StatusLine statusLine = httpResponse.getStatusLine();
                         int statusCode = statusLine.getStatusCode();

                         responseHeaders = convertHeaders(httpResponse.getAllHeaders());
                         // Handle cache validation.
                         if (statusCode == HttpStatus.SC_NOT_MODIFIED) {

                             Entry entry = request.getCacheEntry();
                             if (entry == null) {
                                 return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED, null,
                                         responseHeaders, true,
                                         SystemClock.elapsedRealtime() - requestStart);
                             }

                             // A HTTP 304 response does not have all header fields. We
                             // have to use the header fields from the cache entry plus
                             // the new ones from the response.
                             // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
                             entry.responseHeaders.putAll(responseHeaders);
                             return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED, entry.data,
                                     entry.responseHeaders, true,
                                     SystemClock.elapsedRealtime() - requestStart);
                         }

                         // Handle moved resources
                         if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                         	String newUrl = responseHeaders.get("Location");
                         	request.setRedirectUrl(newUrl);
                         }

                         // Some responses such as 204s do not have content.  We must check.
                         if (httpResponse.getEntity() != null) {
                           responseContents = entityToBytes(httpResponse.getEntity());
                         } else {
                           // Add 0 byte response as a way of honestly representing a
                           // no-content request.
                           responseContents = new byte[0];
                         }

                         // if the request is slow, log it.
                         long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                         logSlowRequests(requestLifetime, request, responseContents, statusLine);

                         if (statusCode < 200 || statusCode > 299) {
                             throw new IOException();
                         }
                         return new NetworkResponse(statusCode, responseContents, responseHeaders, false,
                                 SystemClock.elapsedRealtime() - requestStart);
                     } catch (SocketTimeoutException e) {
                         attemptRetryOnException("socket", request, new TimeoutError());
                     } catch (ConnectTimeoutException e) {
                         attemptRetryOnException("connection", request, new TimeoutError());
                     } catch (MalformedURLException e) {
                         throw new RuntimeException("Bad URL " + request.getUrl(), e);
                     } catch (IOException e) {
                         int statusCode = 0;
                         NetworkResponse networkResponse = null;
                         if (httpResponse != null) {
                             statusCode = httpResponse.getStatusLine().getStatusCode();
                         } else {
                             throw new NoConnectionError(e);
                         }
                         if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY ||
                         		statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                         	VolleyLog.e("Request at %s has been redirected to %s", request.getOriginUrl(), request.getUrl());
                         } else {
                         	VolleyLog.e("Unexpected response code %d for %s", statusCode, request.getUrl());
                         }
                         if (responseContents != null) {
                             networkResponse = new NetworkResponse(statusCode, responseContents,
                                     responseHeaders, false, SystemClock.elapsedRealtime() - requestStart);
                             if (statusCode == HttpStatus.SC_UNAUTHORIZED ||
                                     statusCode == HttpStatus.SC_FORBIDDEN) {
                                 attemptRetryOnException("auth",
                                         request, new AuthFailureError(networkResponse));
                             } else if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY ||
                             			statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                                 attemptRetryOnException("redirect",
                                         request, new RedirectError(networkResponse));
                             } else {
                                 // TODO: Only throw ServerError for 5xx status codes.
                                 throw new ServerError(networkResponse);
                             }
                         } else {
                             throw new NetworkError(e);
                         }
                     }
                 }
             }

           ...
         }>

第323行调用httpResponse = mHttpStack.performRequest(request, headers);<br>
进行网络请求，请求具体实现是HttpURLConnection和HttpClient，有兴趣可以看一下源码。<br>
之后会将服务器返回的数据组装成一个NetworkResponse对象进行返回.<br>

在NetworkDispatcher中收到了NetworkResponse这个返回值后又会调用Request的parseNetworkResponse()方法来解析<br>
NetworkResponse中的数据，以及将数据写入到缓存，这个方法的实现是交给Request的子类来完成的，<br>
因为不同种类的Request解析的方式也肯定不同。还记得我们在上一篇文章中学习的自定义Request的方式吗？<br>
其中parseNetworkResponse()这个方法就是必须要重写的。<br>

在解析完了NetworkResponse中的数据之后，又会调用ExecutorDelivery的postResponse()方法来回调解析出的数据，代码如下：<br>


        <public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
             request.markDelivered();
             request.addMarker("post-response");
             mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
         }
        >

其中，在mResponsePoster的execute()方法中传入了一个ResponseDeliveryRunnable对象，就可以保证该对象中的<br>
run()方法就是在主线程当中运行的了，我们看下run()方法中的代码是什么样的：<br>

        <private class ResponseDeliveryRunnable implements Runnable {
             private final Request mRequest;
             private final Response mResponse;
             private final Runnable mRunnable;

             public ResponseDeliveryRunnable(Request request, Response response, Runnable runnable) {
                 mRequest = request;
                 mResponse = response;
                 mRunnable = runnable;
             }

             @SuppressWarnings("unchecked")
             @Override
             public void run() {
                 // If this request has canceled, finish it and don't deliver.
                 if (mRequest.isCanceled()) {
                     mRequest.finish("canceled-at-delivery");
                     return;
                 }
                 // Deliver a normal response or error, depending.
                 if (mResponse.isSuccess()) {
                     mRequest.deliverResponse(mResponse.result);
                 } else {
                     mRequest.deliverError(mResponse.error);
                 }
                 // If this is an intermediate response, add a marker, otherwise we're done
                 // and the request can be finished.
                 if (mResponse.intermediate) {
                     mRequest.addMarker("intermediate-response");
                 } else {
                     mRequest.finish("done");
                 }
                 // If we have been provided a post-delivery runnable, run it.
                 if (mRunnable != null) {
                     mRunnable.run();
                 }
            }
         }  >

我们发现在在460行调用了mRequest.deliverResponse(mResponse.result);这个是在我们自定义Request需要重写的方法，<br>
这样，每条请求都会回调到这个方法，最后我们再在这个方法中将响应的数据回调到Response.Listener的onResponse()方法中就可以了。<br>

<br>
<br>
<br>



