/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.torproject.android.vpn;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

public class OrbotVpnService extends VpnService implements Handler.Callback, Runnable {
    private static final String TAG = "OrbotVpnService";

    private String mServerAddress = "127.0.0.1";
    private int mServerPort = 9040;
    private PendingIntent mConfigureIntent;

    private Handler mHandler;
    private Thread mThread;

    private ParcelFileDescriptor mInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        // Stop the previous session by interrupting the thread.
        if (mThread != null) {
            mThread.interrupt();
        }
        // Start a new session by creating a new thread.
        mThread = new Thread(this, "OrbotVpnThread");
        mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mThread != null) {
            mThread.interrupt();
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public synchronized void run() {
        try {
            Log.i(TAG, "Starting");

            // If anything needs to be obtained using the network, get it now.
            // This greatly reduces the complexity of seamless handover, which
            // tries to recreate the tunnel without shutting down everything.
            // In this demo, all we need to know is the server address.
            InetSocketAddress server = new InetSocketAddress(
                    mServerAddress, mServerPort);
            mHandler.sendEmptyMessage(R.string.connecting);
      
            run(server);
            
              } catch (Exception e) {
            Log.e(TAG, "Got " + e.toString());
            try {
                mInterface.close();
            } catch (Exception e2) {
                // ignore
            }
            mHandler.sendEmptyMessage(R.string.disconnected);

        } finally {
           
        }
    }
    /*
    @Override
    public synchronized void run() {
        try {
            Log.i(TAG, "Starting");

            // If anything needs to be obtained using the network, get it now.
            // This greatly reduces the complexity of seamless handover, which
            // tries to recreate the tunnel without shutting down everything.
            // In this demo, all we need to know is the server address.
            InetSocketAddress server = new InetSocketAddress(
                    mServerAddress, mServerPort);

            // We try to create the tunnel for several times. The better way
            // is to work with ConnectivityManager, such as trying only when
            // the network is avaiable. Here we just use a counter to keep
            // things simple.
            for (int attempt = 0; attempt < 10; ++attempt) {
                mHandler.sendEmptyMessage(R.string.connecting);

                // Reset the counter if we were connected.
                if (run(server)) {
                    attempt = 0;
                }

                // Sleep for a while. This also checks if we got interrupted.
                Thread.sleep(3000);
            }
            Log.i(TAG, "Giving up");
        } catch (Exception e) {
            Log.e(TAG, "Got " + e.toString());
        } finally {
            try {
                mInterface.close();
            } catch (Exception e) {
                // ignore
            }
            mInterface = null;

            mHandler.sendEmptyMessage(R.string.disconnected);
            Log.i(TAG, "Exiting");
        }
    }*/

    DatagramChannel mTunnel = null;


    private boolean run(InetSocketAddress server) throws Exception {
        boolean connected = false;
        
        android.os.Debug.waitForDebugger();
        
            // Create a DatagramChannel as the VPN tunnel.
        	mTunnel = DatagramChannel.open();

            // Protect the tunnel before connecting to avoid loopback.
            if (!protect(mTunnel.socket())) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }

            // Connect to the server.
            mTunnel.connect(server);

            // For simplicity, we use the same thread for both reading and
            // writing. Here we put the tunnel into non-blocking mode.
            mTunnel.configureBlocking(false);

            // Authenticate and configure the virtual network interface.
            handshake();

            // Now we are connected. Set the flag and show the message.
            connected = true;
            mHandler.sendEmptyMessage(R.string.connected);

            
            new Thread ()
            {
            	
            	public void run ()
            	{
            		DatagramChannel tunnel = mTunnel;

              	  // Allocate the buffer for a single packet.
                    ByteBuffer packet = ByteBuffer.allocate(8096);
		            // Packets to be sent are queued in this input stream.
		            FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());
		         // Read the outgoing packet from the input stream.
		            int length;
		            
		            try
		            {
		            	while (true)
		            	{
				            while ((length = in.read(packet.array())) > 0) {
				                // Write the outgoing packet to the tunnel.
				                packet.limit(length);
				                tunnel.write(packet);
				                packet.clear();
				
				            }
		            	}
		            }
		            catch (IOException e)
		            {
		            	e.printStackTrace();
		            }
		            
            	}
            }.start();
            
            
            new Thread ()
            {
            	
            	public void run ()
            	{
            		DatagramChannel tunnel = mTunnel;

              	  // Allocate the buffer for a single packet.
                    ByteBuffer packet = ByteBuffer.allocate(8096);
		            // Packets received need to be written to this output stream.
		            FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());
		
		            while (true)
		            {
		                try
		                {
			                // Read the incoming packet from the tunnel.
			                int length;
			                while ((length = tunnel.read(packet)) > 0)
			                {
			                        // Write the incoming packet to the output stream.
			                    out.write(packet.array(), 0, length);
			                    
			                    packet.clear();
		
			                }
		                }
		                catch (IOException ioe)
		                {
		                	ioe.printStackTrace();
		                }
	            	}
            	}
            }.start();

        return connected;
    }

    private void handshake() throws Exception {
       
    	if (mInterface == null)
    	{
	        Builder builder = new Builder();
	        
	        builder.setMtu(1400);
	        builder.addAddress("10.0.0.2",32);
	        builder.addRoute("0.0.0.0",0);
	        builder.addDnsServer("8.8.8.8");
	       // builder.addSearchDomain("torproject.org");
	        
	        // Close the old interface since the parameters have been changed.
	        try {
	            mInterface.close();
	        } catch (Exception e) {
	            // ignore
	        }
	        
	
	        // Create a new interface using the builder and save the parameters.
	        mInterface = builder.setSession(mServerAddress)
	                .setConfigureIntent(mConfigureIntent)
	                .establish();
    	}
    }

}
