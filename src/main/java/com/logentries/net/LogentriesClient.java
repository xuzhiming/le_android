package com.logentries.net;

import android.util.Log;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;


public class LogentriesClient
{
	// Logentries server endpoints for logs data.
	private static final String LE_TOKEN_API = "logs.grouk.com";//"data.logentries.com"; // For token-based stream input

	private static final String LE_HTTP_API = "https://api.grouk.com/webhook/57a3164de4b044eb495b987c/2b01d68e9eca413e84aaf4b9dc8b3913";//"http://js.logentries.com/v1/logs/";   // For HTTP-based input.

	private static final String MG_TOKEN = "57a3164de4b044eb495b987c/2b01d68e9eca413e84aaf4b9dc8b3913";//"http://js.logentries.com/v1/logs/";   // For HTTP-based input.
	private static final String TAG = "LogentriesAndroidLogger";


	// Port number for unencrypted HTTP PUT/Token TCP logging on Logentries server.
	private static final int LE_PORT = 9821;

	// Port number for SSL HTTP PUT/TLS Token TCP logging on Logentries server.
	private static final int LE_SSL_PORT = 9822;

	static final Charset UTF8 = Charset.forName("UTF-8");

	private final SSLSocketFactory sslFactory;

	private Socket socket;              // The socket, connected to the Token API endpoint (Token-based input only!)
	private OutputStream stream;        // Data stream to the endpoint, where log messages go (Token-based input only!)

	private HttpClient httpClient;      // HTTP client, used for communicating with HTTP API endpoint.
	private HttpPost postRequest;       // Request object, used to forward data put requests.

	private String endpointToken;   // Token, that points to the exact endpoint - the log object, where the data goes.

	private boolean sslChoice = false;  // Use SSL layering for the Socket?
	private boolean httpChoice = false; // Use HTTP input instead of token-based stream input?

	// Datahub-related attributes.
	private String dataHubServer = null;
	private int dataHubPort = 0;
	private boolean useDataHub = false;

	// The formatter used to prepend logs with the endpoint token for Token-based input.
	private StringBuilder streamFormatter = new StringBuilder();

	public LogentriesClient(boolean useHttpPost, boolean useSsl, boolean isUsingDataHub, String server, int port,
							String token)
			throws InstantiationException, IllegalArgumentException {

		if(useHttpPost && isUsingDataHub) {
			throw new IllegalArgumentException("'httpPost' parameter cannot be set to true if 'isUsingDataHub' " +
					"is set to true.");
		}

		//SSL to be used only with TCP Token
		if(useHttpPost && useSsl) {
			throw new IllegalArgumentException("'httpPost' parameter cannot be set to true if 'useSsl' " +
					"is set to true.");
		}

		if(token == null || token.isEmpty()) {
//			throw new IllegalArgumentException("Token parameter cannot be empty!");
		}

		useDataHub = isUsingDataHub;
		sslChoice = useSsl;
		httpChoice = useHttpPost;
		endpointToken = token;

		if(useDataHub) {
			if (server == null || server.isEmpty()) {
				throw new InstantiationException("'server' parameter is mandatory if 'isUsingDatahub' parameter " +
						"is set to true.");
			}
			if (port <= 0 || port > 65535) {
				throw new InstantiationException("Incorrect port number " + Integer.toString(port) + ". Port number must " +
						"be greater than zero and less than 65535.");
			}
			dataHubServer = server;
			dataHubPort = port;
		}
		if(useSsl)
		{
			try {
				sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			} catch (Exception e) {
				throw new InstantiationException("Cannot create LogentriesClient instance. Error: " + e.getMessage());
			}
		}
		else
		{
			sslFactory = null;
		}
	}

	public int getPort()
	{
		if(useDataHub)
		{
			return dataHubPort;
		} else {
			return sslChoice ? LE_SSL_PORT : LE_PORT;
		}
	}

	public String getAddress()
	{
		if(useDataHub)
		{
			return dataHubServer;
		} else {
			if(httpChoice) {
				return LE_HTTP_API;
			}
			return LE_TOKEN_API;
		}
	}

	public void connect() throws IOException, IllegalArgumentException {
		if(httpChoice) {
			httpClient = new DefaultHttpClient();
			postRequest =  new HttpPost(getAddress() + endpointToken);
		} else {
			Socket s = new Socket(getAddress(), getPort());
			if(sslChoice) {
				if(sslFactory == null) {
					throw new IllegalArgumentException("SSL Socket Factory is not initialized!");
				}
				SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(s, getAddress(), getPort(), true);
				sslSocket.setTcpNoDelay(true);
				socket = sslSocket;
			} else {
				socket = s;
			}
			stream = socket.getOutputStream();
		}
	}

	public void write(String data) throws IOException
	{
		if(!httpChoice) {
			// Token-based or DataHub output mode - we're using plain stream forwarding via the socket.
			if(stream == null) {
				throw new IOException("OutputStream is not initialized!");
			}

			/*
			streamFormatter.setLength(0); // Erase all previous data.
			streamFormatter.append(endpointToken).append(" ");
			streamFormatter.append(data);
			// For Token-based input it is mandatory for the message to has '\n' at the end to be
			// ingested by the endpoint correctly.
			if(!data.endsWith("\n")) {
				streamFormatter.append("\n");
			}
			stream.write(streamFormatter.toString().getBytes(UTF8));
			*/
			if (data.endsWith("\n"))
				data = data.substring(0, data.length() - 1);
			byte[] buffer = encodePacket(data);

			Log.d(TAG, "log send: " + buffer);

			stream.write(buffer);
			stream.flush();

			Log.d(TAG, "log sent");


		} else {
			// HTTP input mode.
			postRequest.setEntity(new StringEntity(data, "UTF8"));
			httpClient.execute(postRequest);
		}
	}

	public static final byte[] intToByteArray(int value) {
		return new byte[] { (byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
	}

	public void sendAuthHeader() throws IOException {
		//only tcp need this
		if (!httpChoice) {
			if(stream == null) {
				throw new IOException("OutputStream is not initialized!");
			}
			JSONObject auth = new JSONObject();
			try {
				auth.put("token", endpointToken);

				byte[] encodeData = encodePacket(auth.toString());
				Log.d(TAG, "authHeader send: " + encodeData);

				stream.write(encodeData);
				stream.flush();

				Log.d(TAG, "authHeader sent");
			} catch (JSONException e) {
				e.printStackTrace();
			}

		}
	}

	//bytes: version->0x7f extension->0x06 packet-length-header->4bytes
	byte[] encodePacket(String rawData) throws UnsupportedEncodingException {

		byte[] rawDataBytes = rawData.getBytes("UTF8");
		int total = 6 + rawDataBytes.length;
		ByteBuffer buffer = ByteBuffer.allocate(total);
		buffer.put((byte)0x7f).put((byte)0x06).putInt(rawDataBytes.length).put(rawDataBytes);

		byte[] read = new byte[buffer.position()];
		buffer.position(0);
		buffer.get(read);
		return read;
	}

	public void close()
	{
		try{
			if (socket != null)
			{
				socket.close();
				socket = null;
			}
		} catch (Exception e) {
			// Just hide the exception - we cannot throw here.
		}
	}
}
