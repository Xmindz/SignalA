package com.zsoft.SignalA.transport.longpolling;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.androidquery.util.AQUtility;
import com.androidquery.util.Constants;
import com.zsoft.SignalA.Connection;
import com.zsoft.SignalA.ConnectionState;
import com.zsoft.SignalA.SignalAUtils;
import com.zsoft.SignalA.SendCallback;

public class ConnectedState extends StopableStateWithCallback {
	protected static final String TAG = "ConnectedState";
	private Object mCallbackLock = new Object();
	private AjaxCallback<JSONObject> mCurrentCallback = null;
	
	public ConnectedState(Connection connection) {
		super(connection);
	}

	@Override
	public ConnectionState getState() {
		return ConnectionState.Connected;
	}

	@Override
	public void Start() {
	}

	@Override
	public void Stop() {
		mConnection.SetNewState(new DisconnectingState(mConnection));
		super.Stop();
	}

	@Override
	public void Send(final CharSequence text, final SendCallback sendCb) {
		if(DoStop())
		{
			sendCb.OnError(new Exception("Connection is about to close"));
			return; 
		}

		AQuery aq = new AQuery(mConnection.getContext());
	    String url = SignalAUtils.EnsureEndsWith(mConnection.getUrl(), "/") +  "send?transport=LongPolling&connectionId=" + mConnection.getConnectionId();

	    AjaxCallback<String> cb = new AjaxCallback<String>() {
			@Override
			public void callback(String url, String result, AjaxStatus status) {
				if(status.getCode() == 200){
					Log.v(TAG, "Message sent: " + text);
					sendCb.OnSent(text);
				}
				else
				{
					Exception ex = new Exception("Error sending message");
					mConnection.OnError(ex);
					sendCb.OnError(ex);
				}
			}
		};
		
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("data", text);
		
		cb.url(url).type(String.class).expire(-1).params(params).method(Constants.METHOD_POST);
		aq.ajax(cb);
	}

	@Override
	protected void OnRun() {
		//AQUtility.setDebug(true);
		//AjaxCallback.setTimeout(90000);
		AQuery aq = new AQuery(mConnection.getContext());
		
		if(DoStop()) return; 

	    String url = SignalAUtils.EnsureEndsWith(mConnection.getUrl(), "/");

	    if (mConnection.getMessageId() == null)
		{
			url += "connect";
		}
	    
	    url += GetReceiveQueryString(mConnection);

		Map<String, Object> params = new HashMap<String, Object>();
		      
		AjaxCallback<JSONObject> cb = new AjaxCallback<JSONObject>()
		{
			@Override
			public void callback(String url, JSONObject json, AjaxStatus status) {
				if(DoStop()) return; 

                try
                {
                    if (json!=null)
                    {
                    	String newMessageId = null;
                    	JSONArray messagesArray = null;
                    	JSONObject transportData = null;
                        boolean disconnected = false;
                        boolean timedOut = false;

            			try {
            				timedOut = json.optInt("T") == 1;	
            				disconnected = json.optBoolean("D", false);
            				newMessageId = json.optString("C");
            				messagesArray = json.getJSONArray("M");
            				//transportData = json.getJSONObject("TransportData");
            			} catch (JSONException e) {
            				mConnection.OnError(new Exception("Error parsing response."));
    						mConnection.SetNewState(new ReconnectingState(mConnection));
            				return;
            			}

                        if (disconnected)
                        {
    						mConnection.SetNewState(new DisconnectedState(mConnection));
    						return;
                        }

                        if (messagesArray != null)
                        {
            				for (int i = 0; i < messagesArray.length(); i++) {
            					//JSONObject m = null;
								try {
									String m = messagesArray.getString(i); //.getJSONObject(i);
	            					mConnection.OnMessage(m.toString());
								} catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
            				}

                            mConnection.setMessageId(newMessageId);

                            //var transportData = result["TransportData"] as JObject;
            		
                            //if (transportData != null)
                            //{
                            //    var groups = (JArray)transportData["Groups"];
                            //    if (groups != null)
                            //    {
                            //        connection.Groups = groups.Select(token => token.Value<string>());
                            //    }
                            //}
                        }
                    }
                    else
                    {
					    mConnection.OnError(new Exception("Error when calling endpoint. Returncode: " + status.getCode()));
						mConnection.SetNewState(new ReconnectingState(mConnection));
                    }
                }
                finally
                {
					mIsRunning.set(false);
					
					// Loop if we are still connected
					if(mConnection.getCurrentState() == ConnectedState.this)
						Run();
                }
			}
		};

		
		synchronized (mCallbackLock) {
			mCurrentCallback = cb;
		}
		//aq.ajax(url, JSONObject.class, cb);
		AjaxCallback.setReuseHttpClient(false);	// To fix wierd timeout issue
		cb.url(url).type(JSONObject.class).expire(-1).params(params).method(Constants.METHOD_POST).timeout(115000);
		aq.ajax(cb);
	}

    protected String GetReceiveQueryString(Connection connection)
    {

            // ?transport={0}&connectionId={1}&messageId={2}&groups={3}&connectionData={4}{5}
		String qs = "?transport=LongPolling";
		qs += "&connectionId=" + connection.getConnectionId();
		if(connection.getMessageId()!=null)
		{
			try {
				qs += "&messageId=" + URLEncoder.encode(connection.getMessageId(), "utf-8");
			} catch (UnsupportedEncodingException e) {
				Log.e(TAG, "Unsupported message encoding error, when encoding messageid.");
			}
		}

        //if (connection.Groups != null && connection.Groups.Any())
        //{
        //    qsBuilder.Append("&groups=" + Uri.EscapeDataString(JsonConvert.SerializeObject(connection.Groups)));
        //}

        //if (data != null)
        //{
        //    qsBuilder.Append("&connectionData=" + data);
        //}

        return qs;
    }

}
