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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import com.runjva.sourceforge.jsocks.protocol.ProxyServer;
import com.runjva.sourceforge.jsocks.server.ServerAuthenticatorNone;

public class OrbotVpnService extends VpnService implements Handler.Callback, Runnable {
    private static final String TAG = "OrbotVpnService";

    private String mServerAddress = "127.0.0.1";
    private int mServerPort = 9040;
    private PendingIntent mConfigureIntent;

    private Handler mHandler;
    private Thread mThread;

    private String mSessionName = "OrbotVPN";
    private ParcelFileDescriptor mInterface;

    private int mSocksProxyPort = 9999;
    
    private boolean mKeepRunning = true;
    
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
     
        startSocksBypass ();
        
        return START_STICKY;
    }
    
    private void startSocksBypass ()
    {
	    Thread thread = new Thread ()
	    {
	    	public void run ()
	    	{
	    
		        try {
					//Socks5Proxy p = new Socks5Proxy("127.0.0.1",9999);
					final ProxyServer server = new ProxyServer(new ServerAuthenticatorNone(null, null));
					server.setVpnService(OrbotVpnService.this);
					server.start(9999, 5, InetAddress.getLocalHost());
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
	    	}
	    };
	    
	    thread.start();
    }

    @Override
    public void onDestroy() {
        if (mThread != null) {
        	mKeepRunning = false;
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
        
            // Create a DatagramChannel as the VPN tunnel.
        	mTunnel = DatagramChannel.open();
        	DatagramSocket s = mTunnel.socket();
        	
            // Protect the tunnel before connecting to avoid loopback.        	
        	if (!protect(s)) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }
        	
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
              	  // Allocate the buffer for a single packet.
            	    ByteBuffer packet = ByteBuffer.allocate(32767);
		           
            	    // Packets to be sent are queued in this input stream.
                    FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());

                    // Packets received need to be written to this output stream.
                    FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());
                    
            	    // We use a timer to determine the status of the tunnel. It
                    // works on both sides. A positive value means sending, and
                    // any other means receiving. We start with receiving.
                    int timer = 0;
            		Log.d(TAG,"tunnel open:" + mTunnel.isOpen() + " connected:" + mTunnel.isConnected());

                    // We keep forwarding packets till something goes wrong.
                    while (true) {
                    	
                    	try
                    	{
	                        // Assume that we did not make any progress in this iteration.
	                        boolean idle = true;
	
	                        // Read the outgoing packet from the input stream.
	                        int length = in.read(packet.array());
	                        if (length > 0) {
	                        	
	                        	Log.d(TAG,"got outgoing packet; length=" + length);
	                            // Write the outgoing packet to the tunnel.
	                            packet.limit(length);	                            
	                            mTunnel.write(packet);
	                            packet.clear();
	
	                            // There might be more outgoing packets.
	                            idle = false;
	
	                            // If we were receiving, switch to sending.
	                            if (timer < 1) {
	                                timer = 1;
	                            }
	                        }
	
	                        // Read the incoming packet from the mTunnel.
	                        length = mTunnel.read(packet);
	                        if (length > 0) { 	          
	                        	
	                        	Log.d(TAG,"got inbound packet; length=" + length);
	                                // Write the incoming packet to the output stream.
	                            out.write(packet.array(), 0, length);	                     
	                            packet.clear();
	
	                            // There might be more incoming packets.
	                            idle = false;
	
	                            // If we were sending, switch to receiving.
	                            if (timer > 0) {
	                                timer = 0;
	                            }
	                        }
	
	                        // If we are idle or waiting for the network, sleep for a
	                        // fraction of time to avoid busy looping.
	                        if (idle) {
	                            Thread.sleep(100);
	
	                            // Increase the timer. This is inaccurate but good enough,
	                            // since everything is operated in non-blocking mode.
	                            timer += (timer > 0) ? 100 : -100;
	
	                            // We are receiving for a long time but not sending.
	                            if (timer < -15000) {
	                               // Switch to sending.
	                                timer = 1;
	                            }
	
	                            // We are sending for a long time but not receiving.
	                            if (timer > 20000) {
	                                //throw new IllegalStateException("Timed out");
	                                //Log.d(TAG,"receiving timed out? timer=" + timer);
	                            }
	                        }
                    	}
                    	catch (Exception e)
                    	{
                    		Log.d(TAG,"error in tunnel",e);
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
	        
	        builder.setMtu(1500);
	        builder.addAddress("10.0.2.0",24);
	        builder.setSession("OrbotVPN");	        	
	        builder.addRoute("0.0.0.0",0);
	        builder.addDnsServer("8.8.8.8");
	   //     builder.addDnsServer("127.0.0.1:5400");
	        // Close the old interface since the parameters have been changed.
	        try {
	            mInterface.close();
	        } catch (Exception e) {  
	            // ignore
	        }
	        
	
	        // Create a new interface using the builder and save the parameters.
	        mInterface = builder.setSession(mSessionName)
	                .setConfigureIntent(mConfigureIntent)
	                .establish();
    	}
    }
    
    private void debugPacket(ByteBuffer packet)
    {
        /*
        for(int i = 0; i < length; ++i)
        {
            byte buffer = packet.get();

            Log.d(TAG, "byte:"+buffer);
        }*/



        int buffer = packet.get();
        int version;
        int headerlength;
        version = buffer >> 4;
        headerlength = buffer & 0x0F;
        headerlength *= 4;
        Log.d(TAG, "IP Version:"+version);
        Log.d(TAG, "Header Length:"+headerlength);

        String status = "";
        status += "Header Length:"+headerlength;

        buffer = packet.get();      //DSCP + EN
        buffer = packet.getChar();  //Total Length

        Log.d(TAG, "Total Length:"+buffer);

        buffer = packet.getChar();  //Identification
        buffer = packet.getChar();  //Flags + Fragment Offset
        buffer = packet.get();      //Time to Live
        buffer = packet.get();      //Protocol

        Log.d(TAG, "Protocol:"+buffer);

        status += "  Protocol:"+buffer;

        buffer = packet.getChar();  //Header checksum

        String sourceIP  = "";
        buffer = packet.get();  //Source IP 1st Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get();  //Source IP 2nd Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get();  //Source IP 3rd Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get();  //Source IP 4th Octet
        sourceIP += buffer;

        Log.d(TAG, "Source IP:"+sourceIP);

        status += "   Source IP:"+sourceIP;

        String destIP  = "";
        buffer = packet.get();  //Destination IP 1st Octet
        destIP += buffer;
        destIP += ".";

        buffer = packet.get();  //Destination IP 2nd Octet
        destIP += buffer;
        destIP += ".";

        buffer = packet.get();  //Destination IP 3rd Octet
        destIP += buffer;
        destIP += ".";

        buffer = packet.get();  //Destination IP 4th Octet
        destIP += buffer;

        Log.d(TAG, "Destination IP:"+destIP);

        status += "   Destination IP:"+destIP;
        /*
        msgObj = mHandler.obtainMessage();
        msgObj.obj = status;
        mHandler.sendMessage(msgObj);
        */

        //Log.d(TAG, "version:"+packet.getInt());
        //Log.d(TAG, "version:"+packet.getInt());
        //Log.d(TAG, "version:"+packet.getInt());

    }

}
